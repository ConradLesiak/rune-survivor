package com.rgs.runesurvivor.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

class HitMarker {
    final String text;
    final Color color = new Color();
    final Vector2 pos = new Vector2();
    float time = 0f;
    float life = 0.8f;

    HitMarker(float x, float y, String text, Color c, float life) {
        this.text = text;
        this.color.set(c);
        this.life = life;
        this.pos.set(x, y);
    }

    boolean update(float delta) {
        time += delta;
        pos.y += 30f * delta;          // drift upward
        return time >= life;
    }

    float alpha() {
        float a = 1f - (time / life);
        return Math.max(0f, Math.min(1f, a));
    }
}
