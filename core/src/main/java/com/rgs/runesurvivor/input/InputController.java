package com.rgs.runesurvivor.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;

/** Collects movement intents; no game logic here. */
public class InputController extends InputAdapter {
    private boolean up, down, left, right;
    private final Vector2 move = new Vector2();

    @Override public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.W:
            case Input.Keys.UP:    up = true;    return true;
            case Input.Keys.S:
            case Input.Keys.DOWN:  down = true;  return true;
            case Input.Keys.A:
            case Input.Keys.LEFT:  left = true;  return true;
            case Input.Keys.D:
            case Input.Keys.RIGHT: right = true; return true;
        }
        return false;
    }

    @Override public boolean keyUp(int keycode) {
        switch (keycode) {
            case Input.Keys.W:
            case Input.Keys.UP:    up = false;    return true;
            case Input.Keys.S:
            case Input.Keys.DOWN:  down = false;  return true;
            case Input.Keys.A:
            case Input.Keys.LEFT:  left = false;  return true;
            case Input.Keys.D:
            case Input.Keys.RIGHT: right = false; return true;
        }
        return false;
    }

    /** Returns a normalized (-1..1) movement vector. */
    public Vector2 getMove(Vector2 out) {
        // Poll as a safety net so missed keyUp events can't latch movement
        boolean upNow    = up    || Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean downNow  = down  || Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        boolean leftNow  = left  || Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean rightNow = right || Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);

        out.set(0, 0);
        if (upNow)    out.y += 1f;
        if (downNow)  out.y -= 1f;
        if (leftNow)  out.x -= 1f;
        if (rightNow) out.x += 1f;
        if (out.len2() > 1f) out.nor(); // prevent faster diagonals
        return out;
    }

    public void reset() { up = down = left = right = false; }
}
