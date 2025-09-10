package com.rgs.runesurvivor.ai;

import com.badlogic.gdx.ai.steer.Steerable;
import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

public class Box2dSteeringEntity implements Steerable<Vector2> {
    private final Body body;
    private float boundingRadius;
    private boolean tagged;
    private boolean independentFacing;

    // Limiter fields
    private float zeroLinearSpeedThreshold = 0.001f;
    private float maxLinearSpeed = 25f;
    private float maxLinearAcceleration = 60f;
    private float maxAngularSpeed = 10f;
    private float maxAngularAcceleration = 30f;

    public Box2dSteeringEntity(Body body, float boundingRadius, boolean independentFacing) {
        this.body = body;
        this.boundingRadius = boundingRadius;
        this.independentFacing = independentFacing;
    }

    public Body getBody() { return body; }

    // --- Steerable ---
    @Override public Vector2 getLinearVelocity() { return body.getLinearVelocity(); }
    @Override public float getAngularVelocity() { return body.getAngularVelocity(); }
    @Override public float getBoundingRadius() { return boundingRadius; }
    @Override public boolean isTagged() { return tagged; }
    @Override public void setTagged(boolean tagged) { this.tagged = tagged; }
    @Override public Vector2 getPosition() { return body.getPosition(); }
    @Override public float getOrientation() { return body.getAngle(); }
    @Override public void setOrientation(float orientation) { body.setTransform(getPosition(), orientation); }
    @Override public float vectorToAngle(Vector2 vector) { return MathUtils.atan2(vector.y, vector.x); }
    @Override public Vector2 angleToVector(Vector2 outVector, float angle) {
        outVector.x = MathUtils.cos(angle);
        outVector.y = MathUtils.sin(angle);
        return outVector;
    }
    @Override public Location<Vector2> newLocation() { return new Box2dLocation(); }

    public boolean isIndependentFacing() { return independentFacing; }
    public void setIndependentFacing(boolean v) { independentFacing = v; }

    // --- Limiter (required by Steerable in gdx-ai) ---
    @Override public float getMaxLinearSpeed() { return maxLinearSpeed; }
    @Override public void setMaxLinearSpeed(float maxLinearSpeed) { this.maxLinearSpeed = maxLinearSpeed; }
    @Override public float getMaxLinearAcceleration() { return maxLinearAcceleration; }
    @Override public void setMaxLinearAcceleration(float maxLinearAcceleration) { this.maxLinearAcceleration = maxLinearAcceleration; }
    @Override public float getMaxAngularSpeed() { return maxAngularSpeed; }
    @Override public void setMaxAngularSpeed(float maxAngularSpeed) { this.maxAngularSpeed = maxAngularSpeed; }
    @Override public float getMaxAngularAcceleration() { return maxAngularAcceleration; }
    @Override public void setMaxAngularAcceleration(float maxAngularAcceleration) { this.maxAngularAcceleration = maxAngularAcceleration; }
    @Override public float getZeroLinearSpeedThreshold() { return zeroLinearSpeedThreshold; }
    @Override public void setZeroLinearSpeedThreshold(float value) { this.zeroLinearSpeedThreshold = value; }

    // Helper: align orientation to current velocity (when not independentFacing)
    public void faceToVelocity() {
        Vector2 v = body.getLinearVelocity();
        if (v.len2() > zeroLinearSpeedThreshold * zeroLinearSpeedThreshold) {
            float newOrientation = vectorToAngle(v);
            body.setAngularVelocity(0);
            body.setTransform(body.getPosition(), newOrientation);
        }
    }

    // --- Simple Location implementation ---
    public static class Box2dLocation implements Location<Vector2> {
        Vector2 position = new Vector2();
        float orientation;
        @Override public Vector2 getPosition() { return position; }
        @Override public float getOrientation() { return orientation; }
        @Override public void setOrientation(float orientation) { this.orientation = orientation; }
        @Override public Location<Vector2> newLocation() { return new Box2dLocation(); }
        @Override public float vectorToAngle(Vector2 vector) { return MathUtils.atan2(vector.y, vector.x); }
        @Override public Vector2 angleToVector(Vector2 outVector, float angle) {
            outVector.x = MathUtils.cos(angle);
            outVector.y = MathUtils.sin(angle);
            return outVector;
        }
    }
}
