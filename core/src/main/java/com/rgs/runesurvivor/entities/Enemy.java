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

    public void update(float delta) {
        if (dead) return;

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

        sprite.setPosition(body.getPosition().x - sprite.getWidth()/2f,
            body.getPosition().y - sprite.getHeight()/2f);
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
