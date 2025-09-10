package com.rgs.runesurvivor.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;

public class HitMarkerSystem {
    public static final Color WHITE = new Color(1f,1f,1f,1f);
    public static final Color GOLD  = new Color(1f,0.84f,0f,1f);

    private final Array<HitMarker> list = new Array<>();
    private final BitmapFont font = new BitmapFont();

    public void spawn(float x, float y, String text, Color color, float life) {
        list.add(new HitMarker(x, y, text, color, life));
    }

    public void update(float delta) {
        for (int i = list.size - 1; i >= 0; i--) {
            if (list.get(i).update(delta)) list.removeIndex(i);
        }
    }

    public void render(SpriteBatch batch) {
        for (HitMarker hm : list) {
            font.setColor(hm.color.r, hm.color.g, hm.color.b, hm.alpha());
            font.draw(batch, hm.text, hm.pos.x, hm.pos.y);
        }
        font.setColor(1,1,1,1);
    }

    public void dispose() { font.dispose(); }
}
