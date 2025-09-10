package com.rgs.runesurvivor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

public class DeathOverlay implements Disposable {

    public interface Listener {
        void onRespawn();
        void onMainMenu();
    }

    private final Stage stage;
    private final Listener listener;

    // runtime-made textures + font
    private final Texture dimTex, panelTex, btnUpTex, btnDownTex;
    private final BitmapFont font;

    private boolean visible = false;

    public DeathOverlay(Listener listener) {
        this.listener = listener;
        this.stage = new Stage(new ExtendViewport(800, 480));

        // tiny runtime textures
        dimTex     = solid(1, 1, new Color(0f, 0f, 0f, 0.55f));
        panelTex   = solid(12, 12, new Color(0.10f, 0.10f, 0.12f, 0.92f));
        btnUpTex   = solid(1, 1, new Color(0.22f, 0.22f, 0.30f, 1f));
        btnDownTex = solid(1, 1, new Color(0.18f, 0.18f, 0.26f, 1f));

        font = new BitmapFont();

        buildUi();
    }

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        // dim
        Image dim = new Image(new TextureRegionDrawable(new TextureRegion(dimTex)));
        dim.setFillParent(true);
        root.addActor(dim);

        // panel
        Table panel = new Table();
        panel.setTouchable(Touchable.enabled);
        panel.setBackground(new TextureRegionDrawable(new TextureRegion(panelTex)));
        panel.pad(18f).defaults().pad(6f);
        root.add(panel).expand().center();

        // styles (no Skin lookups)
        Label.LabelStyle lStyle = new Label.LabelStyle(font, Color.WHITE);

        TextureRegionDrawable up   = new TextureRegionDrawable(new TextureRegion(btnUpTex));
        TextureRegionDrawable down = new TextureRegionDrawable(new TextureRegion(btnDownTex));
        TextButton.TextButtonStyle bStyle = new TextButton.TextButtonStyle();
        bStyle.font = font;
        bStyle.up   = up;
        bStyle.down = down;
        bStyle.over = down;

        Label title = new Label("You Died", lStyle);
        title.setFontScale(1.4f);
        title.setAlignment(Align.center);

        TextButton respawn  = new TextButton("Respawn", bStyle);
        TextButton mainMenu = new TextButton("Main Menu", bStyle);

        panel.add(title).padBottom(12f).row();
        panel.add(respawn).width(220f).height(48f).row();
        panel.add(mainMenu).width(220f).height(48f).row();

        // callbacks
        respawn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (listener != null) listener.onRespawn();
            }
        });
        mainMenu.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (listener != null) listener.onMainMenu();
            }
        });
    }

    private static Texture solid(int w, int h, Color c) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setColor(c);
        pm.fill();
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    public Stage getStage() { return stage; }

    public void show() {
        if (visible) return;
        visible = true;
        stage.getRoot().setVisible(true);
        stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        Gdx.input.setInputProcessor(stage);
    }

    public void hide() {
        visible = false;
        stage.getRoot().setVisible(false);
    }

    public boolean isVisible() { return visible; }

    public void render(float delta) {
        if (!visible) return;
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void dispose() {
        stage.dispose();
        font.dispose();
        dimTex.dispose();
        panelTex.dispose();
        btnUpTex.dispose();
        btnDownTex.dispose();
    }
}
