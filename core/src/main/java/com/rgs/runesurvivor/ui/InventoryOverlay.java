package com.rgs.runesurvivor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import java.util.function.Consumer;

public class InventoryOverlay {
    private final Stage stage;
    private final BitmapFont font = new BitmapFont();
    private final Array<Texture> toDispose = new Array<>();

    private final Table dimLayer;
    private final Table root;

    // UI
    private final TextButton equipBtn;
    private final TextButton closeBtn;

    private boolean swordEquipped;
    private final Consumer<Boolean> onEquipSword; // callback to inform game (true=equip, false=unequip)

    // item display
    private final Texture swordTex;

    public InventoryOverlay(boolean initiallyEquipped, Consumer<Boolean> onEquipSword) {
        this.onEquipSword = onEquipSword;
        this.swordEquipped = initiallyEquipped;

        stage = new Stage(new ExtendViewport(800, 480));

        // Background dimmer
        dimLayer = new Table();
        dimLayer.setFillParent(true);
        dimLayer.setBackground(new NinePatchDrawable(new NinePatch(makeTex(new Color(0,0,0,0.45f)), 0,0,0,0)));
        stage.addActor(dimLayer);

        // Root content
        root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        LabelStyle titleStyle = new LabelStyle(font, Color.WHITE);
        TextButtonStyle btnStyle = makeButtonStyle(new Color(0.15f, 0.15f, 0.18f, 0.9f));

        Label title = new Label("Inventory", titleStyle);
        title.setFontScale(2f);

        // Sword row
        swordTex = new Texture("sword1.png");
        Image swordImg = new Image(swordTex);
        float icon = 96f;
        swordImg.setSize(icon, icon);
        swordImg.setScaling(com.badlogic.gdx.utils.Scaling.fit);

        equipBtn = new TextButton(swordEquipped ? "Unequip" : "Equip", btnStyle);
        equipBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                swordEquipped = !swordEquipped;
                equipBtn.setText(swordEquipped ? "Unequip" : "Equip");
                if (onEquipSword != null) onEquipSword.accept(swordEquipped);
            }
        });

        closeBtn = new TextButton("Close", btnStyle);
        closeBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                hide();
            }
        });

        // Layout
        Table itemRow = new Table();
        itemRow.add(swordImg).size(icon, icon).padRight(16f);
        itemRow.add(new Label("Sword", new LabelStyle(font, Color.WHITE))).padRight(16f);
        itemRow.add(equipBtn).width(200f).height(56f);

        float btnW = 240f, btnH = 60f;
        root.add(title).padBottom(28f).row();
        root.add(itemRow).padBottom(24f).row();
        root.add(closeBtn).width(btnW).height(btnH);

        hide();
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
        stage.getViewport().update(w, h, true);
    }

    public void dispose() {
        stage.dispose();
        font.dispose();
        if (swordTex != null) swordTex.dispose();
        for (Texture t : toDispose) t.dispose();
        toDispose.clear();
    }

    public void setSwordEquipped(boolean equipped) {
        this.swordEquipped = equipped;
        if (equipBtn != null) equipBtn.setText(equipped ? "Unequip" : "Equip");
    }

    // ---- style helpers ----
    private TextButtonStyle makeButtonStyle(Color base) {
        TextButtonStyle s = new TextButtonStyle();
        s.font = font;
        s.fontColor = Color.WHITE;
        s.up   = new NinePatchDrawable(new NinePatch(makeTex(base), 0,0,0,0));
        s.over = new NinePatchDrawable(new NinePatch(makeTex(scale(base, 1.15f)), 0,0,0,0));
        s.down = new NinePatchDrawable(new NinePatch(makeTex(scale(base, 0.9f)), 0,0,0,0));
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
        return new Color(Math.min(c.r*mul,1f), Math.min(c.g*mul,1f), Math.min(c.b*mul,1f), c.a);
    }
}
