package com.rgs.runesurvivor.save;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.TimeUtils;
import com.rgs.runesurvivor.entities.Player;

public class SaveManager {

    private static final String KEY_ISLAND_SEED = "1";
    private static final String PREF_NAME = "RuneSurvivorSave";
    private static final int SAVE_VERSION = 1;

    private final Preferences prefs;

    public SaveManager() {
        this.prefs = Gdx.app.getPreferences(PREF_NAME);
    }

    public boolean hasSave() {
        return prefs.contains("version") && prefs.getInteger("version", 0) >= 1;
    }

    public void wipe() {
        prefs.clear();
        prefs.flush();
    }

    public void savePlayer(Player p) {
        Body b = p.getBody();

        prefs.putInteger("version", SAVE_VERSION);
        prefs.putLong("savedAtMs", TimeUtils.millis());

        // Position
        prefs.putFloat("player_x", b.getPosition().x);
        prefs.putFloat("player_y", b.getPosition().y);

        // Stats
        prefs.putFloat("hp_max", p.getMaxHp());
        prefs.putFloat("hp_current", p.getCurrentHp());
        prefs.putFloat("attack", p.getAttack());
        prefs.putFloat("critChance", p.getCritChance());
        prefs.putFloat("critMult", p.getCritMultiplier());
        prefs.putFloat("moveSpeed", p.getMoveSpeed());

        // Equipment
        prefs.putBoolean("swordEquipped", p.isSwordEquipped());

        prefs.flush();
    }

    /** Returns true if load succeeded (a save existed). */
    public boolean loadPlayer(Player p) {
        if (!hasSave()) return false;

        // Position
        float x = prefs.getFloat("player_x", 0f);
        float y = prefs.getFloat("player_y", 0f);
        p.setPosition(x, y);

        // Stats (with clamping/sanity)
        float maxHp = Math.max(1f, prefs.getFloat("hp_max", 100f));
        float curHp = MathUtils.clamp(prefs.getFloat("hp_current", maxHp), 0f, maxHp);
        p.setMaxHp(maxHp);
        p.setCurrentHp(curHp);

        p.setAttack(Math.max(0f, prefs.getFloat("attack", 20f)));
        p.setCritChance(MathUtils.clamp(prefs.getFloat("critChance", 0.2f), 0f, 1f));
        p.setCritMultiplier(Math.max(1f, prefs.getFloat("critMult", 2f)));
        p.setMoveSpeed(Math.max(0f, prefs.getFloat("moveSpeed", p.getMoveSpeed())));

        // Equipment
        p.setSwordEquipped(prefs.getBoolean("swordEquipped", false));

        return true;
    }

    // Get existing seed, or create & persist a new one (stable across runs)
    public long getOrCreateIslandSeed() {
        if (!prefs.contains(KEY_ISLAND_SEED)) {
            long seed = com.badlogic.gdx.math.MathUtils.random(Long.MIN_VALUE, Long.MAX_VALUE);
            prefs.putLong(KEY_ISLAND_SEED, seed);
            prefs.flush();
            return seed;
        }
        return prefs.getLong(KEY_ISLAND_SEED, 1L);
    }

    // Optional: explicit setter (e.g., for "New World")
    public void setIslandSeed(long seed) {
        prefs.putLong(KEY_ISLAND_SEED, seed);
        prefs.flush();
    }
}
