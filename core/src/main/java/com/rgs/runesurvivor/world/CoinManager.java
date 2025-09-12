package com.rgs.runesurvivor.world;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.rgs.runesurvivor.entities.Player;

public class CoinManager implements Disposable {

    public static class Coin {
        public final Vector2 pos = new Vector2();
        public final Sprite sprite;
        public final int amount;

        Coin(float x, float y, int amount, Sprite s) {
            pos.set(x, y);
            sprite = s;
            sprite.setOriginCenter();
            sprite.setPosition(x - s.getWidth() * 0.5f, y - s.getHeight() * 0.5f);
            this.amount = amount;
        }
    }

    private final Array<Coin> coins = new Array<>();
    private final Texture coinTex;
    private final float coinSize = 36f;      // visible size
    private final float pickupRadius = 10f;  // auto-pickup distance

    public CoinManager() {
        coinTex = new Texture("coin1.png");
        coinTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    public void spawn(float x, float y, int amount) {
        Sprite s = new Sprite(coinTex);
        s.setSize(coinSize, coinSize);
        coins.add(new Coin(x, y, amount, s));
    }

    public void update(float delta, Player player) {
        if (player == null) return;

        // Approx player “body radius” from sprite size (works even if your fixture radius isn’t exposed)
        float playerRadius = 0.35f * Math.max(player.getWidth(), player.getHeight());
        float effective = pickupRadius + playerRadius;
        float r2 = effective * effective;

        Vector2 pp = player.getBody().getPosition();
        for (int i = coins.size - 1; i >= 0; i--) {
            Coin c = coins.get(i);
            float dx = c.pos.x - pp.x, dy = c.pos.y - pp.y;
            if (dx * dx + dy * dy <= r2) {
                player.addGold(c.amount);
                coins.removeIndex(i);
            }
        }
    }

    public void render(SpriteBatch batch) {
        for (int i = 0; i < coins.size; i++) coins.get(i).sprite.draw(batch);
    }

    @Override public void dispose() {
        coinTex.dispose();
        coins.clear();
    }
}
