package com.rgs.runesurvivor.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.ObjectSet;
import com.rgs.runesurvivor.input.InputController;
import com.rgs.runesurvivor.world.WorldManager;
import com.rgs.runesurvivor.world.EnemyManager;
import com.rgs.runesurvivor.world.HitMarkerSystem;

public class Player {
    private final Body body;
    private final Sprite sprite;
    private final Texture texture;
    private final InputController input;
    private final Vector2 tmpMove = new Vector2();

    // ------- Movement -------
    private float moveSpeed = 100f;
    private float maxSpeed  = 100f;
    private static final float DAMPING_MOVE = 0.5f;
    private static final float DAMPING_IDLE = 8f;

    // ------- Facing / sword overlay -------
    private boolean facingRight = true;
    private boolean swordEquipped = false; // you can toggle via inventory
    private Texture swordTex;
    private Sprite swordSprite;
    private static final float SWORD_SIZE = 80f;
    private static final float SWORD_OFFSET_Y_IDLE = 10f;
    private static final float SWORD_OFFSET_X_RIGHT_IDLE = 60f;
    private static final float SWORD_OFFSET_X_LEFT_IDLE  = -60f;

    // Swing state (orientation baseline)
    private boolean swingFacingRight = true;
    private float swingBaselineDeg = 0f;

    // If your sword art points RIGHT at 0°, leave 0f.
    // If it points UP, use -90f; DOWN: +90f; LEFT: 180f.
    private static final float SWORD_TEX_RIGHT_DEG = 0f;

    // ------- Combat stats -------
    private float maxHp = 100f;
    private float currentHp = 100f;

    private float attack = 20f;
    private float critChance = 0.20f;      // 20%
    private float critMultiplier = 2.0f;   // 2x

    // ------- Sword swing state -------
    private boolean attacking = false;
    private float attackCooldown = 0.35f;   // seconds between swings
    private float cooldownTimer = 0f;

    private float swingDuration = 0.22f;    // swing time
    private float swingTimer = 0f;
    private float swingArcDeg = 120f;       // total arc
    private float aimAngleRad = 0f;         // aim at click time
    private float hitAngleWidthDeg = 28f;   // how "thick" the blade is for hits
    private float swingRadius = 88f;        // distance of blade center from player center
    private final ObjectSet<Enemy> hitThisSwing = new ObjectSet<>();

    // --- Stamina ---
    private float maxStamina = 100f;
    private float stamina = 100f;
    private float staminaRegenPerSec = 20f;   // regen when not dashing
    private float dashCost = 30f;

    // --- Dash ---
    private boolean dashing = false;
    private float dashTimer = 0f;
    private float dashDuration = 0.18f;       // seconds
    private float dashSpeed = 180f;           // target dash speed (u/s)
    private float dashMaxSpeed = 200f;        // temporary clamp while dashing
    private final Vector2 dashDir = new Vector2(1, 0);

    // Long dash via position-lerp (ignores Box2D speed cap safely)
    private final Vector2 dashStartPos = new Vector2();
    private final Vector2 dashEndPos   = new Vector2();
    private float dashDistance = 0f;     // computed per dash (~10x old distance)

    // --- Dash cooldown ---
    private float dashCooldown = 2f;   // ← change this default length (seconds)
    private float dashCooldownTimer = 0f;
    public void setDashCooldown(float s) { dashCooldown = Math.max(0f, s); }
    public float getDashCooldown() { return dashCooldown; }


    public Player(WorldManager worldManager, float startX, float startY, InputController input) {
        this.input = input;

        texture = new Texture("player1.png");
        sprite  = new Sprite(texture);
        float size = 100f;
        sprite.setSize(size, size);

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.DynamicBody;
        bd.position.set(startX, startY);
        body = worldManager.getWorld().createBody(bd);

        CircleShape shape = new CircleShape();
        shape.setRadius(size / 2f);

        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.density = 1f;
        fd.friction = 0.5f;
        fd.restitution = 0.2f;
        body.createFixture(fd);
        shape.dispose();
    }

    // ---------------- Movement & sprite sync ----------------
    public void update() {
        input.getMove(tmpMove);

        if (tmpMove.isZero(0.0001f)) {
            body.setLinearDamping(dashing ? 0.1f : DAMPING_IDLE);
            Vector2 v = body.getLinearVelocity();
            if (!dashing && v.len2() < 0.0001f) body.setLinearVelocity(0f, 0f);
        } else if (!dashing) { // only apply walk acceleration if not dashing
            body.setLinearDamping(DAMPING_MOVE);
            tmpMove.scl(moveSpeed);
            Vector2 v = body.getLinearVelocity();
            Vector2 impulse = tmpMove.sub(v).scl(body.getMass());
            body.applyLinearImpulse(impulse, body.getWorldCenter(), true);
        }

        // Dash maintenance
        updateDash(Gdx.graphics.getDeltaTime());

        // Clamp (dash has a higher cap)
        Vector2 vel = body.getLinearVelocity();
        float cap = dashing ? dashMaxSpeed : maxSpeed;
        if (vel.len2() > cap * cap) {
            vel.nor().scl(cap);
            body.setLinearVelocity(vel);
        }

        // Facing from movement if not attacking
        final float eps = 0.05f;
        if (!attacking) {
            if (vel.x > eps && !facingRight) {
                facingRight = true;
                if (sprite.isFlipX()) sprite.flip(true, false);
                } else if (vel.x < -eps && facingRight) {
                facingRight = false;
                if (!sprite.isFlipX()) sprite.flip(true, false);
            }
        }

        // Sync player sprite to body
        sprite.setPosition(
            body.getPosition().x - sprite.getWidth() / 2f,
            body.getPosition().y - sprite.getHeight() / 2f
        );

        // Idle sword (no attack)
        if (swordEquipped && swordSprite != null && !attacking) {
            float offsetX = facingRight ? SWORD_OFFSET_X_RIGHT_IDLE : SWORD_OFFSET_X_LEFT_IDLE;
            float offsetY = SWORD_OFFSET_Y_IDLE;

            swordSprite.setSize(SWORD_SIZE, SWORD_SIZE);
            swordSprite.setOriginCenter();
            swordSprite.setRotation(SWORD_TEX_RIGHT_DEG);   // same rotation for both facings
            syncSwordFlipToFacing();                         // ← flip horizontally if facing left

            swordSprite.setPosition(
                sprite.getX() + sprite.getWidth()/2f - SWORD_SIZE/2f + offsetX,
                sprite.getY() + sprite.getHeight()/2f - SWORD_SIZE/2f + offsetY
            );
        }

        // Cooldown tick
        if (cooldownTimer > 0f) cooldownTimer -= Gdx.graphics.getDeltaTime();

        if (dashCooldownTimer > 0f) dashCooldownTimer -= Gdx.graphics.getDeltaTime();

        // Stamina regen (no regen while dashing)
        if (!dashing && stamina < maxStamina) {
            stamina = Math.min(maxStamina, stamina + staminaRegenPerSec * Gdx.graphics.getDeltaTime());
        }
    }

    // ---------------- Combat (called from GameScreen with mouse) ----------------
    public void updateCombat(float delta, Vector2 mouseWorld, boolean attackPressed, boolean attackHeld,
                             EnemyManager enemyManager, HitMarkerSystem hits) {
        if (!swordEquipped) return;

        // --- Attack start ---
        // Start attack on click OR keep chaining while held when cooldown is ready
        if (!attacking && cooldownTimer <= 0f && (attackPressed || attackHeld)) {
            // Aim toward mouse
            Vector2 center = body.getPosition();
            aimAngleRad = MathUtils.atan2(mouseWorld.y - center.y, mouseWorld.x - center.x);

            // Face according to aim (flip PLAYER sprite if you’re using flip)
            boolean wantRight = MathUtils.cos(aimAngleRad) >= 0f;
            if (wantRight != facingRight) {
                facingRight = wantRight;
                sprite.flip(true, false); // keep/remove based on your player art
            }

            // Begin swing
            attacking = true;
            swingTimer = 0f;
            hitThisSwing.clear();

            // Ensure sword sprite exists
            if (swordTex == null) swordTex = new Texture("sword1.png");
            if (swordSprite == null) {
                swordSprite = new Sprite(swordTex);
                swordSprite.setSize(SWORD_SIZE, SWORD_SIZE);
                swordSprite.setOriginCenter();
            }

            // Lock swing facing/baseline; swing vertical flip only when facing left (as you set before)
            swingFacingRight = facingRight;
            swingBaselineDeg = SWORD_TEX_RIGHT_DEG;
            swordSprite.setFlip(false, !swingFacingRight); // flipY when left only
        }

        // --- Swing progress & hits ---
        if (attacking) {
            swingTimer += delta;
            float t = MathUtils.clamp(swingTimer / swingDuration, 0f, 1f);

            // Direction: right = CW (-), left = CCW (+)
            float dir = swingFacingRight ? -1f : +1f;
            float aimDeg  = aimAngleRad * MathUtils.radiansToDegrees;
            float halfArc = swingArcDeg * 0.5f;
            float curDeg  = aimDeg - dir * halfArc + dir * (t * swingArcDeg);
            float curRad  = curDeg * MathUtils.degreesToRadians;

            // Position the sword along the arc
            float cx = body.getPosition().x + MathUtils.cos(curRad) * swingRadius;
            float cy = body.getPosition().y + MathUtils.sin(curRad) * swingRadius;

            swordSprite.setSize(SWORD_SIZE, SWORD_SIZE);
            swordSprite.setOriginCenter();
            swordSprite.setRotation(curDeg + swingBaselineDeg);

            // Keep vertical flip ONLY for left-facing swings; no horizontal flip during swing
            swordSprite.setFlip(false, !swingFacingRight);

            swordSprite.setPosition(cx - SWORD_SIZE / 2f, cy - SWORD_SIZE / 2f);

            // Hit detection window
            float hitHalfRad = (hitAngleWidthDeg * 0.5f) * MathUtils.degreesToRadians;
            float reach = swingRadius + SWORD_SIZE * 0.5f + 20f;

            for (Enemy e : enemyManager.getEnemies()) {
                if (hitThisSwing.contains(e)) continue;

                Vector2 ep = e.getPosition();
                float dx = ep.x - body.getPosition().x;
                float dy = ep.y - body.getPosition().y;
                float dist2 = dx * dx + dy * dy;
                if (dist2 > reach * reach) continue;

                float eAng = MathUtils.atan2(dy, dx);
                float deltaAng = wrapToPi(eAng - curRad); // wrap to [-PI, PI]
                if (Math.abs(deltaAng) <= hitHalfRad) {
                    boolean crit = MathUtils.random() < critChance;
                    float dmg = crit ? attack * critMultiplier : attack;

                    e.damage(dmg);
                    hitThisSwing.add(e);

                    hits.spawn(
                        ep.x, ep.y + 35f,
                        Integer.toString(Math.round(dmg)),
                        crit ? HitMarkerSystem.GOLD : HitMarkerSystem.WHITE,
                        0.8f
                    );
                }
            }

            // End swing
            if (swingTimer >= swingDuration) {
                attacking = false;
                cooldownTimer = attackCooldown;

                // Restore vertical flip to OFF; keep current horizontal state unchanged
                if (swordSprite != null) {
                    swordSprite.setFlip(swordSprite.isFlipX(), false);
                }
            }
        }
    }

    // ---------------- Rendering ----------------
    // Draw sword separately (between enemies and player) so sword is above enemies but below player
    public void renderSword(SpriteBatch batch) {
        if (swordEquipped && swordSprite != null) {
            swordSprite.draw(batch);
        }
    }

    public void render(SpriteBatch batch) {
        sprite.draw(batch);
    }

    // HP bar
    public void renderHpBar(ShapeRenderer sr) {
        if (maxHp <= 0f) return;
        float ratio = Math.max(0f, currentHp / maxHp);

        float barWidth  = sprite.getWidth();
        float barHeight = 8f;
        float x = sprite.getX();
        float y = sprite.getY() - 12f;

        sr.setColor(0f, 0f, 0f, 0.5f);
        sr.rect(x, y, barWidth, barHeight);

        sr.setColor(0f, 1f, 0f, 1f);
        sr.rect(x, y, barWidth * ratio, barHeight);
    }

    // ---------------- API ----------------
    public Body getBody() { return body; }
    public void dispose() {
        texture.dispose();
        if (swordTex != null) swordTex.dispose();
    }

    public void setMoveSpeed(float s) { moveSpeed = s; maxSpeed = s; }
    public float getMoveSpeed() { return moveSpeed; }

    public boolean isSwordEquipped() { return swordEquipped; }
    public void setSwordEquipped(boolean equipped) {
        if (equipped == this.swordEquipped) return;
        this.swordEquipped = equipped;

        if (!equipped) {
            // cancel swing & hide overlay
            attacking = false;
            swingTimer = 0f;
            cooldownTimer = 0f;
            // (keep swordSprite allocated but it won't draw when unequipped)
            return;
        }
        if (swordTex == null) swordTex = new Texture("sword1.png");
        if (swordSprite == null) {
            swordSprite = new Sprite(swordTex);
            swordSprite.setSize(SWORD_SIZE, SWORD_SIZE);
            swordSprite.setOriginCenter();
        }
    }


    // Stats
    public float getMaxHp() { return maxHp; }
    public float getCurrentHp() { return currentHp; }
    public void setMaxHp(float v) { maxHp = Math.max(1f, v); currentHp = Math.min(currentHp, maxHp); }
    public void setCurrentHp(float v) { currentHp = MathUtils.clamp(v, 0f, maxHp); }
    public void heal(float v) { setCurrentHp(currentHp + v); }
    public void damageHp(float v) { setCurrentHp(currentHp - v); }

    public void setAttack(float v) { attack = Math.max(0f, v); }
    public void setCritChance(float v) { critChance = MathUtils.clamp(v, 0f, 1f); }
    public void setCritMultiplier(float v) { critMultiplier = Math.max(1f, v); }

    private static float wrapToPi(float a) {
        // Wrap angle in radians to [-PI, PI]
        a = (a + MathUtils.PI) % MathUtils.PI2;
        if (a < 0f) a += MathUtils.PI2;
        return a - MathUtils.PI;
    }

    private void syncSwordFlipToFacing() {
        if (swordSprite == null) return;
        boolean wantFlipX = !facingRight;           // flip horizontally when facing left
        if (swordSprite.isFlipX() != wantFlipX) {
            swordSprite.flip(true, false);          // toggle to desired flipX state
        }
    }

    public float getAttack() { return attack; }
    public float getCritChance() { return critChance; }
    public float getCritMultiplier() { return critMultiplier; }

    public void setPosition(float x, float y) {
        body.setTransform(x, y, body.getAngle());
        // also snap sprite so we don't wait for update() to sync
        sprite.setPosition(x - sprite.getWidth()/2f, y - sprite.getHeight()/2f);
    }

    public float getMaxStamina() { return maxStamina; }
    public float getStamina() { return stamina; }
    public void  setMaxStamina(float v) { maxStamina = Math.max(1f, v); stamina = Math.min(stamina, maxStamina); }
    public void  setStamina(float v) { stamina = MathUtils.clamp(v, 0f, maxStamina); }
    public void  setStaminaRegen(float v) { staminaRegenPerSec = Math.max(0f, v); }
    public void  setDashCost(float v) { dashCost = Math.max(0f, v); }
    public void  setDashSpeed(float v) { dashSpeed = Math.max(0f, v); dashMaxSpeed = Math.max(dashSpeed, dashMaxSpeed); }
    public boolean isDashing() { return dashing; }

    public boolean tryStartDash(Vector2 mouseWorld) {
        if (dashing) return false;
        if (dashCooldownTimer > 0f) return false;
        if (stamina < dashCost) return false;

        // Aim direction (fallback to facing)
        Vector2 center = body.getPosition();
        dashDir.set(mouseWorld.x - center.x, mouseWorld.y - center.y);
        if (dashDir.isZero(0.0001f)) dashDir.set(facingRight ? 1f : -1f, 0f);
        dashDir.nor();

        // ~10x previous dash distance: base = dashSpeed * dashDuration
        float base = dashSpeed * dashDuration;
        dashDistance = base * 5f;                 // ← main change: MUCH farther

        // Raycast to clamp end before static colliders (e.g., water edges)
        com.badlogic.gdx.physics.box2d.World w = body.getWorld();
        dashStartPos.set(center);
        Vector2 desiredEnd = new Vector2(center).mulAdd(dashDir, dashDistance);

        final float[] minFrac = {1f}; // 1 = no hit
        w.rayCast((fixture, point, normal, fraction) -> {
            // Ignore self and non-static bodies (dash passes through enemies)
            if (fixture.getBody() == body) return -1f;
            if (fixture.getBody().getType() != BodyDef.BodyType.StaticBody) return 1f;
            if (fraction < minFrac[0]) minFrac[0] = fraction;
            return 1f; // continue to find closest
        }, dashStartPos, desiredEnd);

        float useFrac = minFrac[0] * 0.98f; // keep a tiny gap from the hit
        if (useFrac < 1f) {
            dashEndPos.set(dashStartPos).mulAdd(dashDir, dashDistance * useFrac);
        } else {
            dashEndPos.set(desiredEnd);
        }

        // Pay cost & start dash
        stamina -= dashCost;
        dashing = true;
        dashTimer = 0f;

        dashCooldownTimer = dashCooldown;    // ← start cooldown (measured from dash start)

        // Optional: cancel swing during dash (uncomment if desired)
        // attacking = false; swingTimer = 0f;

        // Reduce damping so the body doesn’t fight setTransform “momentum”
        body.setLinearDamping(0.1f);
        return true;
    }

    private void updateDash(float delta) {
        if (!dashing) return;

        dashTimer += delta;
        float t = MathUtils.clamp(dashTimer / dashDuration, 0f, 1f);

        // Smooth ease-out (feel free to change)
        float s = t * (2f - t);

        // Lerp position along dash path (ignores Box2D velocity caps)
        float nx = MathUtils.lerp(dashStartPos.x, dashEndPos.x, s);
        float ny = MathUtils.lerp(dashStartPos.y, dashEndPos.y, s);
        body.setTransform(nx, ny, body.getAngle());

        if (dashTimer >= dashDuration) {
            dashing = false;
            // Kill excess velocity and restore normal damping
            body.setLinearVelocity(0f, 0f);
            body.setLinearDamping(DAMPING_MOVE);
        }
    }

    public float getWidth()  { return sprite.getWidth(); }
    public float getHeight() { return sprite.getHeight(); }
    public boolean isFacingRight() { return facingRight; }
    public com.badlogic.gdx.graphics.Texture getTexture() { return texture; }
}
