package com.rgs.runesurvivor.entities;

import com.badlogic.gdx.ai.steer.SteeringAcceleration;
import com.badlogic.gdx.ai.steer.behaviors.Wander;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.rgs.runesurvivor.ai.Box2dSteeringEntity;
import com.rgs.runesurvivor.world.WorldManager;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.rgs.runesurvivor.world.HitMarkerSystem;
import com.rgs.runesurvivor.entities.Player;

public class Enemy {
    private static Texture sharedTexture;

    private final Sprite sprite;
    private final Body body;
    private final Box2dSteeringEntity steerable;
    private final Wander<Vector2> wander;
    private final SteeringAcceleration<Vector2> steeringOut = new SteeringAcceleration<>(new Vector2());

    // Health
    private float maxHp = 50f;
    private float hp = 50f;
    private boolean dead = false;

    private enum AttackState { IDLE, WINDUP, STRIKE, COOLDOWN }
    private AttackState atkState = AttackState.IDLE;
    private float atkTimer = 0f;

    // Tunables
    private float atkDamage = 5f;
    private float atkWindup = 0.25f;     // telegraph time
    private float atkStrike = 0.08f;     // hit window
    private float atkCooldown = 2f;   // recovery
    private float atkRange = 60f;        // from enemy center
    private float atkArcDeg = 80f;       // swing width
    private float knockback = 120f;      // impulse to player on hit

    // Cached facing/aim during an attack
    private float atkAimDeg = 0f;
    private boolean didHitThisAttack = false;

    public Enemy(WorldManager world, float x, float y) {
        if (sharedTexture == null) sharedTexture = new Texture("enemy1.png");
        sprite = new Sprite(sharedTexture);
        float size = 50f;
        sprite.setSize(size, size);

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.DynamicBody;
        bd.position.set(x, y);
        body = world.getWorld().createBody(bd);

        CircleShape shape = new CircleShape();
        shape.setRadius(size / 2f);

        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.density = 1.0f;
        fd.friction = 0.6f;
        fd.restitution = 0.1f;
        body.createFixture(fd);
        shape.dispose();

        body.setLinearDamping(0.5f);
        body.setAngularDamping(2f);

        steerable = new Box2dSteeringEntity(body, size / 2f, false);
        steerable.setMaxLinearSpeed(24f);
        steerable.setMaxLinearAcceleration(120f);

        wander = new Wander<>(steerable)
            .setFaceEnabled(false)
            .setWanderRadius(20f)
            .setWanderRate(3.0f)
            .setWanderOffset(10f);
    }

    public void update(float delta, Player player, HitMarkerSystem hits) {
        // If attacking, don't add wander forces
        if (atkState == AttackState.IDLE) {
            wander.calculateSteering(steeringOut);
            if (!steeringOut.linear.isZero()) {
                Vector2 force = new Vector2(steeringOut.linear).scl(body.getMass());
                body.applyForceToCenter(force, true);
            }
            float max = steerable.getMaxLinearSpeed();
            Vector2 vel = body.getLinearVelocity();
            if (vel.len2() > max * max) {
                vel.nor().scl(max);
                body.setLinearVelocity(vel);
            }
            if (!steerable.isIndependentFacing()) steerable.faceToVelocity();
        } else {
            // Slight damping while attacking so they don't slide much
            body.setLinearDamping(2.0f);
        }

        // Attack state machine
        updateAttack(delta, player, hits);

        // Sync sprite
        sprite.setPosition(body.getPosition().x - sprite.getWidth()/2f,
            body.getPosition().y - sprite.getHeight()/2f);
    }

    // ===== Attack state machine =====
    private void updateAttack(float delta, Player player, HitMarkerSystem hits) {
        Vector2 ep = body.getPosition();
        Vector2 pp = player.getBody().getPosition();

        switch (atkState) {
            case IDLE: {
                // Start an attack if player within range
                float dist = ep.dst(pp);
                if (dist <= atkRange + player.getWidth()*0.25f) {
                    atkState = AttackState.WINDUP;
                    atkTimer = 0f;
                    didHitThisAttack = false;
                    atkAimDeg = MathUtils.atan2(pp.y - ep.y, pp.x - ep.x) * MathUtils.radiansToDegrees;
                }
            } break;

            case WINDUP: {
                atkTimer += delta;
                if (atkTimer >= atkWindup) {
                    atkState = AttackState.STRIKE;
                    atkTimer = 0f;
                    // Re-lock aim right before striking
                    atkAimDeg = MathUtils.atan2(pp.y - ep.y, pp.x - ep.x) * MathUtils.radiansToDegrees;
                }
            } break;

            case STRIKE: {
                atkTimer += delta;
                // Deal damage once if player is inside the strike arc
                if (!didHitThisAttack) {
                    if (isPointInArc(pp.x, pp.y, ep.x, ep.y, atkAimDeg, atkArcDeg, atkRange + player.getWidth()*0.25f)) {
                        didHitThisAttack = true;
                        player.damageHp(atkDamage);

                        // Knockback
                        Vector2 dir = new Vector2(pp).sub(ep);
                        if (!dir.isZero()) {
                            dir.nor().scl(knockback * player.getBody().getMass());
                            player.getBody().applyLinearImpulse(dir, player.getBody().getWorldCenter(), true);
                        }

                        // Hit marker (red) at player
                        hits.spawn(pp.x, pp.y + 40f, Integer.toString(Math.round(atkDamage)), HitMarkerSystem.RED, 0.8f);
                    }
                }
                if (atkTimer >= atkStrike) {
                    atkState = AttackState.COOLDOWN;
                    atkTimer = 0f;
                }
            } break;

            case COOLDOWN: {
                atkTimer += delta;
                if (atkTimer >= atkCooldown) {
                    atkState = AttackState.IDLE;
                    atkTimer = 0f;
                    // restore damping after attack
                    body.setLinearDamping(0.5f);
                }
            } break;
        }
    }

    // Point-in-arc test (center x0,y0; arc centered at aimDeg, width arcDeg, radius r)
    private boolean isPointInArc(float x, float y, float x0, float y0, float aimDeg, float arcDeg, float r) {
        float dx = x - x0, dy = y - y0;
        float dist2 = dx*dx + dy*dy;
        if (dist2 > r*r) return false;

        float ang = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        float da = wrapDeg(ang - aimDeg);
        return Math.abs(da) <= (arcDeg * 0.5f);
    }

    private float wrapDeg(float a) {
        a = (a + 180f) % 360f;
        if (a < 0f) a += 360f;
        return a - 180f;
    }

    // ===== Telegraph / strike drawing =====
    public void renderAttack(ShapeRenderer sr) {
        if (atkState == AttackState.IDLE) return;

        float cx = body.getPosition().x;
        float cy = body.getPosition().y;

        if (atkState == AttackState.WINDUP) {
            float t = MathUtils.clamp(atkTimer / atkWindup, 0f, 1f);
            float radius = MathUtils.lerp(20f, atkRange, t);
            drawSector(sr, cx, cy, radius, atkAimDeg - atkArcDeg*0.5f, atkArcDeg,
                28, 1f, 0.6f, 0f, 0.35f); // orange, transparent
        } else if (atkState == AttackState.STRIKE) {
            drawSector(sr, cx, cy, atkRange, atkAimDeg - atkArcDeg*0.5f, atkArcDeg,
                32, 1f, 0.1f, 0.1f, 0.55f); // red, bolder
        }
    }

    // Filled wedge using triangle fan
    private void drawSector(ShapeRenderer sr, float cx, float cy, float radius,
                            float startDeg, float degrees, int segments,
                            float r, float g, float b, float a) {
        float step = degrees / segments;
        float ang = startDeg;
        sr.setColor(r, g, b, a);
        for (int i = 0; i < segments; i++) {
            float a0 = (ang + i * step) * MathUtils.degreesToRadians;
            float a1 = (ang + (i + 1) * step) * MathUtils.degreesToRadians;
            float x0 = cx + MathUtils.cos(a0) * radius;
            float y0 = cy + MathUtils.sin(a0) * radius;
            float x1 = cx + MathUtils.cos(a1) * radius;
            float y1 = cy + MathUtils.sin(a1) * radius;
            sr.triangle(cx, cy, x0, y0, x1, y1);
        }
    }

    public void render(SpriteBatch batch) { if (!dead) sprite.draw(batch); }

    public void dispose(WorldManager world) {
        world.getWorld().destroyBody(body);
    }

    public Vector2 getPosition() { return body.getPosition(); }

    // Health API
    public void damage(float amount) {
        if (dead) return;
        hp -= amount;
        if (hp <= 0f) { hp = 0f; dead = true; }
    }
    public boolean isDead() { return dead; }

    public static void disposeSharedTexture() {
        if (sharedTexture != null) { sharedTexture.dispose(); sharedTexture = null; }
    }
}
