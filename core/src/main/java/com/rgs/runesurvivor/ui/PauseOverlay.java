package com.rgs.runesurvivor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

public class PauseOverlay {
    private final Stage stage;
    private final BitmapFont font = new BitmapFont();
    private final Array<Texture> toDispose = new Array<>();

    private final Table root;        // centers content & reflows on resize
    private final Table dimLayer;    // optional translucent dim background

    public PauseOverlay(Runnable onContinue, Runnable onMainMenu) {
        stage = new Stage(new ExtendViewport(800, 480));

        // ---- Styles (match menus) ----
        LabelStyle titleStyle = new LabelStyle(font, Color.WHITE);
        TextButtonStyle btnStyle = makeButtonStyle(new Color(0.15f, 0.15f, 0.18f, 0.9f));

        // ---- Dimming layer (full-screen translucent panel) ----
        dimLayer = new Table();
        dimLayer.setFillParent(true);
        dimLayer.setBackground(new NinePatchDrawable(new NinePatch(makeTex(new Color(0, 0, 0, 0.45f)), 0,0,0,0)));
        stage.addActor(dimLayer);

        // ---- Root content (centered column) ----
        root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        Label title = new Label("Paused", titleStyle);
        title.setFontScale(2f);

        TextButton continueBtn = new TextButton("Continue", btnStyle);
        TextButton mainMenuBtn = new TextButton("Main Menu", btnStyle);

        continueBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                onContinue.run();
            }
        });
        mainMenuBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                onMainMenu.run();
            }
        });

        float btnW = 260f, btnH = 64f, padY = 18f;
        root.add(title).padBottom(36f).row();
        root.add(continueBtn).width(btnW).height(btnH).padBottom(padY).row();
        root.add(mainMenuBtn).width(btnW).height(btnH);

        hide(); // start hidden
    }

    public Stage getStage() { return stage; }

    public void show() {
        dimLayer.setVisible(true);
        root.setVisible(true);
        root.setTouchable(Touchable.enabled);
        stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
    }

    public void hide() {
        dimLayer.setVisible(false);
        root.setVisible(false);
        root.setTouchable(Touchable.disabled);
    }

    public boolean isVisible() { return root.isVisible(); }

    public void actAndDraw(float delta) {
        stage.act(delta);
        stage.draw();
    }

    public void resize(int w, int h) {
        stage.getViewport().update(w, h, true); // Tables re-center automatically
    }

    public void dispose() {
        stage.dispose();
        font.dispose();
        for (Texture t : toDispose) t.dispose();
        toDispose.clear();
    }

    // ---- Helpers: generate hover/pressed backgrounds (no external skin) ----
    private TextButtonStyle makeButtonStyle(Color base) {
        TextButtonStyle s = new TextButtonStyle();
        s.font = font;
        s.fontColor = Color.WHITE;

        s.up   = new NinePatchDrawable(new NinePatch(makeTex(base), 0,0,0,0));
        s.over = new NinePatchDrawable(new NinePatch(makeTex(scale(base, 1.15f)), 0,0,0,0));  // hover brighter
        s.down = new NinePatchDrawable(new NinePatch(makeTex(scale(base, 0.9f)), 0,0,0,0));   // pressed darker
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
