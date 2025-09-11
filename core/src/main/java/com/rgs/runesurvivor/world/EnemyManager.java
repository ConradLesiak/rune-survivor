package com.rgs.runesurvivor.world;

import com.badlogic.gdx.ai.GdxAI;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.rgs.runesurvivor.entities.Enemy;

public class EnemyManager {
    private final WorldManager worldManager;
    private final IslandRenderer island; // <-- water checks
    private final Array<Enemy> enemies = new Array<>();
    private float spawnTimer = 0f;

    private int   maxEnemies        = 10;
    private float spawnInterval     = 1.0f;  // seconds
    private float minPlayerDistance = 150f;

    // Dynamic radii (multipliers of the view half-diagonal)
    private float spawnRadiusMult   = 2.0f;  // where new enemies appear
    private float despawnRadiusMult = 2.6f;  // beyond this they’re removed

    public EnemyManager(WorldManager worldManager, IslandRenderer island) {
        this.worldManager = worldManager;
        this.island = island;
    }

    /**
     * @param cameraCenter world-space camera position
     * @param playerPos    player position (for min spawn distance)
     * @param viewWidth    viewport width in world units
     * @param viewHeight   viewport height in world units
     */
    public void update(float delta, Vector2 cameraCenter, Vector2 playerPos,
                       float viewWidth, float viewHeight,
                       com.rgs.runesurvivor.entities.Player player,
                       com.rgs.runesurvivor.world.HitMarkerSystem hits) {

        GdxAI.getTimepiece().update(delta);

        float halfDiag = 0.5f * (float) Math.sqrt(viewWidth * viewWidth + viewHeight * viewHeight);
        float spawnR   = halfDiag * spawnRadiusMult;
        float despawnR = halfDiag * despawnRadiusMult;
        float despawnR2 = despawnR * despawnR;

        spawnTimer += delta;
        if (enemies.size < maxEnemies && spawnTimer >= spawnInterval) {
            spawnTimer = 0f;
            Vector2 pos = chooseValidSpawn(cameraCenter, playerPos, spawnR);
            enemies.add(new Enemy(worldManager, pos.x, pos.y));
        }

        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);

            // Despawn if far
            Vector2 ep = e.getPosition();
            float dx = ep.x - cameraCenter.x, dy = ep.y - cameraCenter.y;
            if (dx*dx + dy*dy > despawnR2) {
                e.dispose(worldManager);
                enemies.removeIndex(i);
                continue;
            }

            e.update(delta, player, hits);
            if (e.isDead()) {
                e.dispose(worldManager);
                enemies.removeIndex(i);
            }
        }
    }

    public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        for (Enemy e : enemies) e.render(batch);
    }

    public Array<Enemy> getEnemies() { return enemies; }

    public void dispose() {
        for (Enemy e : enemies) e.dispose(worldManager);
        enemies.clear();
        Enemy.disposeSharedTexture();
    }

    // --- helpers ---
    private Vector2 chooseValidSpawn(Vector2 center, Vector2 playerPos, float radius) {
        // Try a few random samples; if water, snap to nearest land
        for (int attempt = 0; attempt < 16; attempt++) {
            Vector2 pos = randomSpawnPos(center, radius);

            // keep some distance from the player
            if (pos.dst2(playerPos) < minPlayerDistance * minPlayerDistance) {
                continue;
            }

            if (island == null) return pos; // no water info available

            if (!island.isWaterWorld(pos.x, pos.y)) {
                return pos; // already on land
            }

            // Snap to nearby land
            Vector2 safe = island.findNearestLand(pos.x, pos.y, 1024);
            if (safe != null && !island.isWaterWorld(safe.x, safe.y)) {
                // re-check player distance after snapping
                if (safe.dst2(playerPos) >= minPlayerDistance * minPlayerDistance) {
                    return safe;
                }
            }
        }

        // Fallback: center-land spawn
        Vector2 centerLand = island != null ? island.findCenterLandSpawn() : new Vector2(center);
        // nudge away from player if too close
        if (centerLand.dst2(playerPos) < minPlayerDistance * minPlayerDistance) {
            float ang = MathUtils.random(0f, MathUtils.PI2);
            centerLand.add(MathUtils.cos(ang) * minPlayerDistance, MathUtils.sin(ang) * minPlayerDistance);
        }
        return centerLand;
    }

    private Vector2 randomSpawnPos(Vector2 center, float radius) {
        float angle = MathUtils.random(0f, MathUtils.PI2);
        float dist  = MathUtils.random(200f, radius);
        return new Vector2(center.x + MathUtils.cos(angle) * dist,
            center.y + MathUtils.sin(angle) * dist);
    }

    // --- Tuners (optional) ---
    public void setMaxEnemies(int n) { this.maxEnemies = n; }
    public void setSpawnInterval(float s) { this.spawnInterval = s; }
    public void setSpawnRadiusMultiplier(float m) { this.spawnRadiusMult = Math.max(0.5f, m); }
    public void setDespawnRadiusMultiplier(float m) { this.despawnRadiusMult = Math.max(1.0f, m); }

    public void despawnWithinRadius(com.badlogic.gdx.math.Vector2 center, float radius) {
        float r2 = radius * radius;
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.getPosition().dst2(center) <= r2) {
                e.dispose(worldManager);
                enemies.removeIndex(i);
            }
        }
    }
}
