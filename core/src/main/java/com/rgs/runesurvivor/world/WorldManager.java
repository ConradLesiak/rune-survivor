package com.rgs.runesurvivor.world;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;

public class WorldManager {
    private World world;
    private Box2DDebugRenderer debug;
    private boolean debugEnabled = false;

    public WorldManager() {
        world = new World(new Vector2(0, 0), true);
        debug = new Box2DDebugRenderer();
    }

    public World getWorld() { return world; }

    public void toggleDebug() { debugEnabled = !debugEnabled; }
    public void setDebug(boolean enabled) { debugEnabled = enabled; }

    public void step() {
        if (world == null) return;
        world.step(1f/60f, 6, 2);
    }

    public void debugRender(Camera cam) {
        if (!debugEnabled || world == null) return;
        debug.render(world, cam.combined);
    }

    public void dispose() {
        if (debug != null) { debug.dispose(); debug = null; }
        if (world != null) { world.dispose(); world = null; }
    }
}
