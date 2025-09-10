package com.rgs.runesurvivor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

public class MainMenuScreen implements Screen {
    private final RuneSurvivorGame game;
    private Stage uiStage;
    private Table root;
    private BitmapFont font;

    private LabelStyle labelStyle;
    private TextButtonStyle buttonStyle;

    // Keep textures to dispose later
    private final Array<Texture> toDispose = new Array<>();

    public MainMenuScreen(RuneSurvivorGame game) {
        this.game = game;

        uiStage = new Stage(new ExtendViewport(800, 480));
        font = new BitmapFont();

        labelStyle = new LabelStyle(font, Color.WHITE);
        buttonStyle = makeButtonStyle(new Color(0.15f, 0.15f, 0.18f, 0.9f)); // base bg

        // Root layout fills the stage and reflows on resize
        root = new Table();
        root.setFillParent(true);
        root.center();
        uiStage.addActor(root);

        Label title = new Label("Rune Survivor", labelStyle);
        title.setFontScale(2f);

        TextButton playBtn = new TextButton("Play", buttonStyle);
        TextButton settingsBtn = new TextButton("Settings", buttonStyle);
        TextButton exitBtn = new TextButton("Exit", buttonStyle);

        // Callbacks
        playBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new GameScreen(game));
                dispose();
            }
        });
        settingsBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new SettingsScreen(game));
                dispose();
            }
        });
        exitBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.exit();
            }
        });

        // Column layout with consistent spacing; Table keeps it centered
        float btnW = 260f, btnH = 64f, padY = 18f;
        root.add(title).padBottom(36f).row();
        root.add(playBtn).width(btnW).height(btnH).padBottom(padY).row();
        root.add(settingsBtn).width(btnW).height(btnH).padBottom(padY).row();
        root.add(exitBtn).width(btnW).height(btnH);
    }

    @Override public void show() {
        // Reconnect input to this screen’s stage
        Gdx.input.setInputProcessor(uiStage);

        // Make sure coordinate mapping is correct right away
        uiStage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        // (optional) set initial focus to root so keyboard nav works
        uiStage.setKeyboardFocus(root);
        uiStage.setScrollFocus(root);
    }

    @Override public void render(float delta) {
        Gdx.gl.glClearColor(0,0,0,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        uiStage.act(delta);
        uiStage.draw();
    }

    @Override public void resize(int w, int h) {
        uiStage.getViewport().update(w, h, true);
    }

    @Override public void hide() {
        // If this screen is going away, only clear input if we still own it
        if (Gdx.input.getInputProcessor() == uiStage) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override public void dispose() { uiStage.dispose(); }
    @Override public void pause() {}
    @Override public void resume() {}

    /** Build a TextButtonStyle with generated hover/pressed backgrounds (no external skin). */
    private TextButtonStyle makeButtonStyle(Color base) {
        TextButtonStyle s = new TextButtonStyle();
        s.font = font;
        s.fontColor = Color.WHITE;

        // Backgrounds: up / over / down made from tinted 4x4 textures
        s.up   = new NinePatchDrawable(new NinePatch(makeTex(base), 0,0,0,0));
        s.over = new NinePatchDrawable(new NinePatch(makeTex(scale(base, 1.15f)), 0,0,0,0));  // hover: brighter
        s.down = new NinePatchDrawable(new NinePatch(makeTex(scale(base, 0.9f)), 0,0,0,0));   // pressed: darker
        s.disabled = new NinePatchDrawable(new NinePatch(makeTex(new Color(base.r, base.g, base.b, 0.4f)), 0,0,0,0));

        s.pressedOffsetX = 0.5f;
        s.pressedOffsetY = -0.5f;
        return s;
    }

    private Texture makeTex(Color c) {
        Pixmap pm = new Pixmap(4, 4, Format.RGBA8888);
        pm.setColor(c);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        toDispose.add(tex);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return tex;
    }

    private Color scale(Color c, float mul) {
        float r = Math.min(c.r * mul, 1f);
        float g = Math.min(c.g * mul, 1f);
        float b = Math.min(c.b * mul, 1f);
        return new Color(r, g, b, c.a);
    }
}
