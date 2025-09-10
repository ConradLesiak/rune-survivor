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

public class SettingsScreen implements Screen {
    private final RuneSurvivorGame game;
    private Stage uiStage;
    private BitmapFont font;

    private LabelStyle labelStyle;
    private TextButtonStyle btnStyle;

    private final Array<Texture> toDispose = new Array<>();

    public SettingsScreen(RuneSurvivorGame game) {
        this.game = game;
        uiStage = new Stage(new ExtendViewport(800, 480));
        font = new BitmapFont();

        labelStyle = new LabelStyle(font, Color.WHITE);
        btnStyle   = makeButtonStyle(new Color(0.15f, 0.15f, 0.18f, 0.9f));

        // Root layout that always fills the stage and re-centers on resize
        Table root = new Table();
        root.setFillParent(true);
        root.center();
        uiStage.addActor(root);

        Label title = new Label("Settings", labelStyle);
        title.setFontScale(1.5f);

        TextButton back = new TextButton("Back", btnStyle);
        back.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new MainMenuScreen(game));
                dispose();
            }
        });

        float btnW = 260f, btnH = 64f;

        root.add(title).padBottom(30f).row();
        // Add your setting toggles/sliders here as new rows...
        root.add(back).width(btnW).height(btnH).padTop(10f);
    }

    @Override public void show() {
        Gdx.input.setInputProcessor(uiStage);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        uiStage.act(delta);
        uiStage.draw();
    }

    @Override
    public void resize(int width, int height) {
        uiStage.getViewport().update(width, height, true); // Table re-centers automatically
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        if (uiStage != null) uiStage.dispose();
        if (font != null) font.dispose();
        for (Texture t : toDispose) t.dispose();
        toDispose.clear();
    }

    private TextButtonStyle makeButtonStyle(Color base) {
        TextButtonStyle s = new TextButtonStyle();
        s.font = font;
        s.fontColor = Color.WHITE;

        s.up   = new NinePatchDrawable(new NinePatch(makeTex(base), 0,0,0,0));
        s.over = new NinePatchDrawable(new NinePatch(makeTex(scale(base, 1.15f)), 0,0,0,0));
        s.down = new NinePatchDrawable(new NinePatch(makeTex(scale(base, 0.9f)), 0,0,0,0));
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
