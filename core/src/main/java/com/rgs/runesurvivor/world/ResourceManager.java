package com.rgs.runesurvivor.world;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

public class ResourceManager implements Disposable {

    public enum NodeKind { TREE, ROCK }

    public static class Node {
        public final NodeKind kind;
        public final Vector2 pos = new Vector2();
        public final float radius;   // collider radius (world units)
        public final Sprite sprite;
        public final Body body;      // static circle collider

        Node(NodeKind kind, float x, float y, float radius, Sprite sprite, Body body) {
            this.kind = kind;
            this.pos.set(x, y);
            this.radius = radius;
            this.sprite = sprite;
            this.body = body;

            this.sprite.setOriginCenter();
            this.sprite.setPosition(x - sprite.getWidth() * 0.5f, y - sprite.getHeight() * 0.5f);
        }
    }

    private final IslandRenderer island;
    private final World world;
    private final Array<Node> nodes = new Array<>();

    // Textures
    private final Texture[] treeTex = new Texture[3];
    private final Texture[] rockTex = new Texture[6];

    // Visual size (world units)
    private final float treeSize = 220f;
    private final float rockSize = 160f;

    // Overlap buffer (extra spacing beyond radius sum)
    private final float overlapBuffer = 8f;

    // Spawn density (scaled by island area) – tweak to taste
    // For a ~20,480 x 20,480 world, this yields ~40 trees and ~60 rocks (much more than 3/6).
    private final float treePerUnits2 = 1f / 10_000_000f; // 1 per 10M u^2
    private final float rockPerUnits2 = 1f / 7_000_000f;  // 1 per 7M u^2
    private final int minTrees = 12, minRocks = 24;

    private static final float COLLIDER_SCALE = 0.95f; // 5% smaller colliders

    public ResourceManager(IslandRenderer island, long islandSeed, World world) {
        this.island = island;
        this.world = world;

        for (int i = 0; i < 3; i++) treeTex[i] = new Texture("tree" + (i+1) + ".png");
        for (int i = 0; i < 6; i++) rockTex[i] = new Texture("rock" + (i+1) + ".png");

        generateNodesDeterministic(islandSeed);
    }

    private void generateNodesDeterministic(long seed) {
        RandomXS128 rng = new RandomXS128(seed ^ 0xD1B54A32D192ED03L);

        float minX = island.getWorldMinX();
        float minY = island.getWorldMinY();
        float w = island.getWorldWidth();
        float h = island.getWorldHeight();
        float area = w * h;

        int treeCount = Math.max(minTrees, Math.round(area * treePerUnits2));
        int rockCount = Math.max(minRocks, Math.round(area * rockPerUnits2));

        // ---- Trees: GRASS or DIRT
        for (int t = 0; t < treeCount; t++) {
            placeNode(rng, NodeKind.TREE, minX, minY, w, h, (tt) ->
                tt == IslandRenderer.TerrainType.GRASS || tt == IslandRenderer.TerrainType.DIRT
            );
        }

        // ---- Rocks: anywhere except BEACH/WATER
        for (int r = 0; r < rockCount; r++) {
            placeNode(rng, NodeKind.ROCK, minX, minY, w, h, (tt) ->
                tt != IslandRenderer.TerrainType.BEACH && tt != IslandRenderer.TerrainType.WATER
            );
        }
    }

    // Terrain predicate
    private interface TerrainFilter { boolean ok(IslandRenderer.TerrainType t); }

    private void placeNode(RandomXS128 rng, NodeKind kind, float minX, float minY, float w, float h, TerrainFilter filter) {
        final int MAX_TRIES = 12000;

        for (int tries = 0; tries < MAX_TRIES; tries++) {
            float x = minX + rng.nextFloat() * w;
            float y = minY + rng.nextFloat() * h;

            IslandRenderer.TerrainType tt = island.getTerrainAtWorld(x, y);
            if (!filter.ok(tt)) continue;

            // Pick sprite + radius
            Sprite sprite;
            float size, radius;
            if (kind == NodeKind.TREE) {
                int idx = variantIndex(x, y, rng, treeTex.length);
                sprite = new Sprite(treeTex[idx]);
                size = treeSize;
                radius = size * 0.42f * COLLIDER_SCALE;
            } else {
                int idx = variantIndex(x, y, rng, rockTex.length);
                sprite = new Sprite(rockTex[idx]);
                size = rockSize;
                radius = size * 0.40f * COLLIDER_SCALE;
            }
            sprite.setSize(size, size);

            // Non-overlap vs existing nodes (circle test)
            boolean ok = true;
            for (int i = 0; i < nodes.size; i++) {
                Node o = nodes.get(i);
                float dx = x - o.pos.x, dy = y - o.pos.y;
                float need = (radius + o.radius + overlapBuffer);
                if (dx * dx + dy * dy < need * need) { ok = false; break; }
            }
            if (!ok) continue;

            // Create static circle body (immovable collider)
            BodyDef bd = new BodyDef();
            bd.type = BodyDef.BodyType.StaticBody;
            bd.position.set(x, y);
            Body body = world.createBody(bd);

            CircleShape cs = new CircleShape();
            cs.setRadius(radius);

            FixtureDef fd = new FixtureDef();
            fd.shape = cs;
            fd.density = 0f;
            fd.friction = 0.9f;
            fd.restitution = 0f;
            body.createFixture(fd);
            cs.dispose();

            nodes.add(new Node(kind, x, y, radius, sprite, body));
            return;
        }
        // If we fail after many tries, we skip this node—density too high near-by; acceptable fallback.
    }

    // Deterministic variant index from position + seed; does not advance rng
    private int variantIndex(float x, float y, RandomXS128 rng, int modulo) {
        long bits = (long)Float.floatToIntBits(x) * 73856093L
            ^ (long)Float.floatToIntBits(y) * 19349663L
            ^ rng.getState(0);
        if (bits < 0) bits = -bits;
        return (int)(bits % modulo);
    }

    public void render(SpriteBatch batch) {
        for (int i = 0; i < nodes.size; i++) nodes.get(i).sprite.draw(batch);
    }

    public Array<Node> getNodes() { return nodes; }

    @Override public void dispose() {
        // Destroy bodies first (world must still be alive when this is called)
        for (int i = nodes.size - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            if (n.body != null && n.body.getWorld() != null) n.body.getWorld().destroyBody(n.body);
        }
        nodes.clear();

        for (Texture t : treeTex) if (t != null) t.dispose();
        for (Texture t : rockTex) if (t != null) t.dispose();
    }
}
