package com.rgs.runesurvivor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Small helper that owns the potion button, handles cooldown, and draws a clock dial above it. */
public class HealthPotionUI implements Disposable {

    public interface Listener {
        /** Return true if a potion was actually consumed (e.g., player healed). */
        boolean onUsePotionRequested();
    }

    private final Stage uiStage;
    private final Listener listener;
    private final ImageButton button;
    private final Texture iconTex;

    private float cooldownSeconds = 10f;
    private float cdRemaining = 0f;

    private final Vector2 tmp = new Vector2();

    public HealthPotionUI(Stage uiStage, String iconPath, float cooldownSeconds, Listener listener) {
        this.uiStage = uiStage;
        this.listener = listener;
        this.cooldownSeconds = cooldownSeconds;

        iconTex = new Texture(Gdx.files.internal(iconPath));
        iconTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        TextureRegionDrawable icon = new TextureRegionDrawable(new TextureRegion(iconTex));
        style.imageUp   = icon;
        style.imageDown = icon.tint(new Color(0.85f, 0.85f, 0.85f, 1f)); // slightly dim on press
        style.imageDisabled = icon.tint(new Color(0.5f, 0.5f, 0.5f, 1f));
        button = new ImageButton(style);

        button.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                requestUse();
            }
        });
    }

    /** Add the button to a table; caller controls size/padding. */
    public ImageButton getButton() { return button; }

    /** Call every frame. */
    public void update(float delta) {
        if (cdRemaining > 0f) {
            cdRemaining -= delta;
            if (cdRemaining < 0f) cdRemaining = 0f;
        }
        button.setDisabled(cdRemaining > 0f);
    }

    /** Keyboard path (e.g., on key '1'). */
    public void requestUse() {
        if (cdRemaining > 0f) return;
        if (listener != null) {
            boolean used = listener.onUsePotionRequested();
            if (used) cdRemaining = cooldownSeconds;
        }
    }

    public boolean isReady() { return cdRemaining <= 0f; }
    public float getCooldownRemaining() { return cdRemaining; }
    public float getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(float s) { cooldownSeconds = Math.max(0f, s); }

    @Override public void dispose() {
        if (iconTex != null) iconTex.dispose();
    }
}
