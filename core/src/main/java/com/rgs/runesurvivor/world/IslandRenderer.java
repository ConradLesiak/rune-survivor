package com.rgs.runesurvivor.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;

/**
 * Massive pixel-art island baked to an FBO (for speed) + Box2D water barriers.
 * Adds circular dirt & gravel patches strictly within the GRASS band (not on edges).
 */
public class IslandRenderer {

    // === Terrain access for external systems ===
    public enum TerrainType { WATER, BEACH, GRASS, DIRT, GRAVEL, ROCK }

    // thresholds (match your build() values)
    private static final float WATER_T = 0.48f;
    private static final float BEACH_T = 0.53f;
    private static final float GRASS_T = 0.80f;

    // cached origin in world space
    private float originX, originY;
    // scratch
    private final com.badlogic.gdx.math.Vector2 tmp = new com.badlogic.gdx.math.Vector2();

    // replace/checkerboard colors with one solid grass tone
    private static final Color GRASS_SOLID   = new Color(0.24f, 0.62f, 0.27f, 1f);

    private final int cols, rows;        // pixel grid size
    private final float cellWorld;       // world units per "pixel"
    private final int seed;
    private final World world;           // for water colliders

    private FrameBuffer fbo;
    private ShapeRenderer sr;
    private Sprite sprite;

    // Terrain data
    private boolean[][] isWater;
    private boolean[][] isGrass;         // grass band (before patches)
    private float[][]   height01;        // 0..1 height after mask

    // Patch mask: 0 = none, 1 = dirt, 2 = gravel
    private byte[][] patchMask;

    // Box2D water edges bodies
    private final Array<Body> waterBodies = new Array<>();

    // Palette
    private static final Color DEEP_WATER    = new Color(0.07f, 0.12f, 0.36f, 1f);
    private static final Color SHALLOW_WATER = new Color(0.12f, 0.45f, 0.55f, 1f);
    private static final Color BEACH         = new Color(0.90f, 0.80f, 0.45f, 1f);
    private static final Color GRASS_DARK    = new Color(0.18f, 0.52f, 0.22f, 1f);
    private static final Color GRASS_LIGHT   = new Color(0.28f, 0.68f, 0.30f, 1f);
    private static final Color DIRT          = new Color(0.55f, 0.40f, 0.22f, 1f);
    private static final Color GRAVEL        = new Color(0.62f, 0.62f, 0.64f, 1f);
    private static final Color ROCK          = new Color(0.55f, 0.55f, 0.58f, 1f);


    public IslandRenderer(int cols, int rows, float cellWorldUnits, int seed, World world) {
        this.cols = cols;
        this.rows = rows;
        this.cellWorld = cellWorldUnits;
        this.seed = seed;
        this.world = world;
        build();
    }

    public void dispose() {
        // remove water colliders first
        if (world != null) {
            for (Body b : waterBodies) world.destroyBody(b);
            waterBodies.clear();
        }

        // make sure any in-flight draws complete before killing GL objects
        try { Gdx.gl.glFinish(); } catch (Throwable ignored) {}

        if (fbo != null) { fbo.dispose(); fbo = null; }
        if (sr  != null) { sr.dispose();  sr = null; }
    }

    public void render(SpriteBatch batch) {
        if (sprite != null) sprite.draw(batch);
    }

    private void build() {
        sr  = new ShapeRenderer();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, cols, rows, false);

        isWater   = new boolean[rows][cols];
        isGrass   = new boolean[rows][cols];
        height01  = new float[rows][cols];
        patchMask = new byte[rows][cols];

        // Height / mask params
        final float freq = 0.0085f;
        final int   octs = 4;
        final float gain = 0.5f;
        final float lac  = 2.0f;
        final float maskPower = 1.6f;

        // Thresholds
        final float WATER_T = 0.48f;
        final float BEACH_T = 0.53f;
        final float GRASS_T = 0.80f;

        // ---- 1) Precompute height & base classification (no drawing yet) ----
        for (int y = 0; y < rows; y++) {
            float ny = (y / (float)rows - 0.5f) * 2f; // [-1,1]
            for (int x = 0; x < cols; x++) {
                float nx = (x / (float)cols - 0.5f) * 2f; // [-1,1]

                float r = (float)Math.sqrt(nx*nx + ny*ny);
                float mask = MathUtils.clamp(1f - (float)Math.pow(r, maskPower), 0f, 1f);

                float h = fbm(x * freq, y * freq, octs, gain, lac); // [-1,1]
                float val = MathUtils.clamp((h * 0.5f + 0.5f) * mask, 0f, 1f);

                height01[y][x] = val;
                isWater[y][x]  = val < WATER_T;
                isGrass[y][x]  = (val >= BEACH_T && val < GRASS_T);
            }
        }

        // ---- 2) Bake circular patches strictly inside the grass band ----
        // Keep them away from the beach/rock edges by requiring an "inner grass" margin.
        final float INNER_GRASS_LOW  = BEACH_T + 0.02f;
        final float INNER_GRASS_HIGH = GRASS_T - 0.03f;

        // --- smaller, circular patches strictly inside grass ---
        int area = cols * rows;
        // More patches (because they’re smaller), but still sparse
        int numPatches = Math.max(120, area / 2200); // ~120–240 on 512x512

        // Tiny radii in "pixel cells"
        int minR = Math.max(2, Math.round(cols * 0.003f));  // ~2 on 512
        int maxR = Math.max(minR + 1, Math.round(cols * 0.010f)); // ~5 on 512

        MathUtils.random.setSeed(seed * 9973L);

        for (int i = 0; i < numPatches; i++) {
            // Pick a center well inside grass
            int cx, cy, tries = 0;
            while (true) {
                cx = MathUtils.random(0, cols - 1);
                cy = MathUtils.random(0, rows - 1);
                float v = height01[cy][cx];
                if (v >= INNER_GRASS_LOW && v < INNER_GRASS_HIGH) break;
                if (++tries > 4000) break; // give up if no spot found
            }
            if (tries > 4000) break;

            int r = MathUtils.random(minR, maxR);
            // Type: bias to dirt
            byte type = MathUtils.random() < 0.6f ? (byte)1 : (byte)2; // 1=dirt, 2=gravel

            int x0 = Math.max(0, cx - r);
            int x1 = Math.min(cols - 1, cx + r);
            int y0 = Math.max(0, cy - r);
            int y1 = Math.min(rows - 1, cy + r);

            int r2 = r * r;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int dx = x - cx;
                    int dy = y - cy;
                    if (dx*dx + dy*dy <= r2) {
                        // Only stamp inside grass (prevents patches touching edges)
                        if (isGrass[y][x] && height01[y][x] >= INNER_GRASS_LOW && height01[y][x] < INNER_GRASS_HIGH) {
                            patchMask[y][x] = type;
                        }
                    }
                }
            }
        }

        // ---- 3) Draw into FBO using arrays + patches ----
        OrthographicCamera cam = new OrthographicCamera(cols, rows);
        cam.setToOrtho(false, cols, rows);

        fbo.begin();
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                float val = height01[y][x];

                if (isWater[y][x]) {
                    sr.setColor(val < 0.7f * 0.48f ? DEEP_WATER : SHALLOW_WATER);
                } else if (val < BEACH_T) {
                    sr.setColor(BEACH);
                } else if (val < GRASS_T) {
                    byte pm = patchMask[y][x];
                    if      (pm == 1) sr.setColor(DIRT);
                    else if (pm == 2) sr.setColor(GRAVEL);
                    else              sr.setColor(GRASS_SOLID); // solid grass, no checkerboard
                } else {
                    sr.setColor(ROCK);
                }

                sr.rect(x, y, 1f, 1f);
            }
        }

        sr.end();
        fbo.end();

        // Build sprite scaled to world units, centered at world origin
        Texture tex = fbo.getColorBufferTexture();
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        TextureRegion region = new TextureRegion(tex);
        region.flip(false, true); // FBO Y-flipped

        sprite = new Sprite(region);
        sprite.setSize(cols * cellWorld, rows * cellWorld);
        sprite.setOriginCenter();
        sprite.setPosition(-sprite.getWidth() / 2f, -sprite.getHeight() / 2f);

        // cache origin for world<->grid mapping
        originX = sprite.getX();
        originY = sprite.getY();

        // Build Box2D edge colliders along water boundaries
        if (world != null) buildWaterColliders();
    }

    // ---------- Build water boundary edges (land<->water cell borders) ----------
    private void buildWaterColliders() {
        final float originX = -cols * cellWorld * 0.5f;
        final float originY = -rows * cellWorld * 0.5f;

        java.util.function.BiConsumer<float[], float[]> addEdge = (a, b) -> {
            BodyDef bd = new BodyDef();
            bd.type = BodyDef.BodyType.StaticBody;
            Body body = world.createBody(bd);

            EdgeShape edge = new EdgeShape();
            edge.set(a[0], a[1], b[0], b[1]);

            FixtureDef fd = new FixtureDef();
            fd.shape = edge;
            fd.friction = 0.9f;
            fd.restitution = 0f;
            fd.density = 0f;
            body.createFixture(fd);
            edge.dispose();

            waterBodies.add(body);
        };

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (!isWater[y][x]) continue;

                float x0 = originX + x * cellWorld;
                float y0 = originY + y * cellWorld;
                float x1 = x0 + cellWorld;
                float y1 = y0 + cellWorld;

                if (y + 1 >= rows || !isWater[y + 1][x]) addEdge.accept(new float[]{x0, y1}, new float[]{x1, y1}); // top
                if (y - 1 < 0    || !isWater[y - 1][x]) addEdge.accept(new float[]{x0, y0}, new float[]{x1, y0}); // bottom
                if (x - 1 < 0    || !isWater[y][x - 1]) addEdge.accept(new float[]{x0, y0}, new float[]{x0, y1}); // left
                if (x + 1 >= cols|| !isWater[y][x + 1]) addEdge.accept(new float[]{x1, y0}, new float[]{x1, y1}); // right
            }
        }
    }

    // --------- fBm (value noise + bilinear) ----------
    private float fbm(float x, float y, int octaves, float gain, float lacunarity) {
        float amp = 1f, sum = 0f, norm = 0f;
        float fx = x, fy = y;
        for (int i = 0; i < octaves; i++) {
            sum += amp * valueNoise(fx, fy);
            norm += amp;
            amp *= gain;
            fx *= lacunarity;
            fy *= lacunarity;
        }
        return (norm > 0f) ? (sum / norm * 2f - 1f) : 0f; // [-1,1]
    }

    // Value noise in [0,1] with smooth interpolation
    private float valueNoise(float x, float y) {
        int xi = (int)Math.floor(x);
        int yi = (int)Math.floor(y);
        float xf = x - xi;
        float yf = y - yi;

        float v00 = hash01(xi,     yi);
        float v10 = hash01(xi + 1, yi);
        float v01 = hash01(xi,     yi + 1);
        float v11 = hash01(xi + 1, yi + 1);

        float u = xf * xf * (3f - 2f * xf);
        float v = yf * yf * (3f - 2f * yf);

        float i1 = MathUtils.lerp(v00, v10, u);
        float i2 = MathUtils.lerp(v01, v11, u);
        return MathUtils.lerp(i1, i2, v); // [0,1]
    }

    private float hash01(int x, int y) {
        int n = x * 374761393 ^ y * 668265263 ^ seed;
        n = (n ^ (n >>> 13)) * 1274126177;
        n = n ^ (n >>> 16);
        return (n & 0x7fffffff) / (float)0x7fffffff;
    }

    // World -> grid
    private boolean worldToCell(float wx, float wy, int[] outXY) {
        int cx = com.badlogic.gdx.math.MathUtils.floor((wx - originX) / cellWorld);
        int cy = com.badlogic.gdx.math.MathUtils.floor((wy - originY) / cellWorld);
        if (cx < 0 || cy < 0 || cx >= cols || cy >= rows) return false;
        outXY[0] = cx; outXY[1] = cy;
        return true;
    }

    /** True if this world position is water (outside the island counts as water). */
    public boolean isWaterWorld(float wx, float wy) {
        int[] xy = new int[2];
        if (!worldToCell(wx, wy, xy)) return true;
        return isWater[xy[1]][xy[0]];
    }

    /** Find the nearest land cell to (wx,wy). Prefers land a bit above beach to avoid shoreline. */
    public com.badlogic.gdx.math.Vector2 findNearestLand(float wx, float wy, int maxRadiusCells) {
        int[] xy = new int[2];
        if (!worldToCell(wx, wy, xy)) {
            // if outside, start from island center
            xy[0] = cols / 2; xy[1] = rows / 2;
        }
        int cx = xy[0], cy = xy[1];

        // search rings expanding from (cx,cy)
        for (int r = 0; r <= maxRadiusCells; r++) {
            int x0 = Math.max(0, cx - r), x1 = Math.min(cols - 1, cx + r);
            int y0 = Math.max(0, cy - r), y1 = Math.min(rows - 1, cy + r);
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    // only check the border of the square ring
                    if (y != y0 && y != y1 && x != x0 && x != x1) continue;
                    // land and a touch above beach to avoid being right on the edge
                    if (!isWater[y][x] && height01[y][x] >= (BEACH_T + 0.01f)) {
                        float wxc = originX + x * cellWorld + cellWorld * 0.5f;
                        float wyc = originY + y * cellWorld + cellWorld * 0.5f;
                        return new com.badlogic.gdx.math.Vector2(wxc, wyc);
                    }
                }
            }
        }
        // fallback: center of island sprite
        return new com.badlogic.gdx.math.Vector2(0f, 0f);
    }

    /** Convenience: land near island center. */
    public com.badlogic.gdx.math.Vector2 findCenterLandSpawn() {
        return findNearestLand(0f, 0f, Math.max(cols, rows));
    }

    // World bounds (where the island sprite is drawn)
    public float getWorldMinX() { return originX; }
    public float getWorldMinY() { return originY; }
    public float getWorldWidth() { return cols * cellWorld; }
    public float getWorldHeight() { return rows * cellWorld; }

    // Classify terrain at world coordinates
    public TerrainType getTerrainAtWorld(float wx, float wy) {
        int[] xy = new int[2];
        if (!worldToCell(wx, wy, xy)) return TerrainType.WATER; // outside treated as water
        int x = xy[0], y = xy[1];
        float val = height01[y][x];

        if (isWater[y][x]) return TerrainType.WATER;
        // Match the thresholds used in build()
        if (val < BEACH_T) return TerrainType.BEACH;
        if (val < GRASS_T) {
            byte pm = patchMask[y][x];
            if (pm == 1)      return TerrainType.DIRT;
            else if (pm == 2) return TerrainType.GRAVEL;
            else              return TerrainType.GRASS;
        }
        return TerrainType.ROCK;
    }
}
