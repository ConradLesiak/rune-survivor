package com.rgs.runesurvivor.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

public class DashFxSystem {

    // ----- Ghost (afterimage) -----
    private static class Ghost {
        float cx, cy;            // center position
        float w, h;              // size
        float rotation;          // degrees
        boolean flipX;           // mirror horizontally
        float t = 0f, life = 0.25f; // seconds
        float startAlpha = 0.55f;

        Ghost(float cx, float cy, float w, float h, float rotation, boolean flipX, float life) {
            this.cx = cx; this.cy = cy;
            this.w = w; this.h = h;
            this.rotation = rotation;
            this.flipX = flipX;
            this.life = life;
        }

        boolean update(float dt) { t += dt; return t >= life; }
        float alpha() {
            float a = 1f - (t / life);
            // ease-out
            return MathUtils.clamp(startAlpha * (a * (2f - a)), 0f, 1f);
        }
    }

    // ----- Shockwave ring -----
    private static class Wave {
        float x, y;
        float r0, r1;          // start/end radius
        float lineWidth = 4f;
        float t = 0f, life = 0.35f;

        Wave(float x, float y, float r0, float r1, float life) {
            this.x = x; this.y = y;
            this.r0 = r0; this.r1 = r1; this.life = life;
        }

        boolean update(float dt) { t += dt; return t >= life; }
        float radius() {
            float u = MathUtils.clamp(t / life, 0f, 1f);
            u = u * (2f - u); // ease-out
            return MathUtils.lerp(r0, r1, u);
        }
        float alpha() { return MathUtils.clamp(1f - (t / life), 0f, 1f); }
    }

    private final Array<Ghost> ghosts = new Array<>();
    private final Array<Wave> waves = new Array<>();

    // API
    public void spawnGhost(float cx, float cy, float w, float h, float rotation, boolean flipX) {
        ghosts.add(new Ghost(cx, cy, w, h, rotation, flipX, 0.22f));
    }

    public void spawnWave(float x, float y, float startR, float endR) {
        waves.add(new Wave(x, y, startR, endR, 0.30f));
    }

    public void update(float dt) {
        for (int i = ghosts.size - 1; i >= 0; i--) if (ghosts.get(i).update(dt)) ghosts.removeIndex(i);
        for (int i = waves.size - 1; i >= 0; i--) if (waves.get(i).update(dt)) waves.removeIndex(i);
    }

    // Draw ghosts with the player's texture (no extra textures needed)
    public void renderGhosts(SpriteBatch batch, Texture playerTexture) {
        if (playerTexture == null) return;
        for (Ghost g : ghosts) {
            batch.setColor(1f, 1f, 1f, g.alpha());
            // draw centered; use negative scaleX to flip
            float originX = g.w * 0.5f, originY = g.h * 0.5f;
            float scaleX = g.flipX ? -1f : 1f;
            batch.draw(playerTexture,
                g.cx - originX, g.cy - originY,
                originX, originY,
                g.w, g.h,
                scaleX, 1f,
                g.rotation,
                0, 0, playerTexture.getWidth(), playerTexture.getHeight(),
                false, false);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    // Draw shockwave rings
    public void renderShapes(ShapeRenderer sr) {
        sr.set(ShapeRenderer.ShapeType.Line);
        for (Wave w : waves) {
            float a = w.alpha();
            sr.setColor(1f, 1f, 1f, a);
            // approximate line width by multiple concentric lines (portable)
            float r = w.radius();
            for (int i = 0; i < 3; i++) {
                sr.circle(w.x, w.y, r + i * 0.8f, 32);
            }
        }
    }
}
