package com.rgs.runesurvivor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.rgs.runesurvivor.world.HitMarkerSystem;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.rgs.runesurvivor.entities.Player;
import com.rgs.runesurvivor.input.InputController;
import com.rgs.runesurvivor.ui.InventoryOverlay;
import com.rgs.runesurvivor.ui.PauseOverlay;
import com.rgs.runesurvivor.world.CameraController;
import com.rgs.runesurvivor.world.EnemyManager;
import com.rgs.runesurvivor.world.WorldManager;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class GameScreen implements Screen {
    private final RuneSurvivorGame game;

    // Stages
    private Stage worldStage;
    private Stage uiStage;

    // Core
    private ShapeRenderer shapeRenderer;
    private WorldManager worldManager;
    private Player player;
    private CameraController cameraController;
    private InputController inputController;
    private EnemyManager enemyManager;

    // Overlays
    private PauseOverlay pauseOverlay;
    private InventoryOverlay inventoryOverlay;
    private boolean paused = false;
    private boolean inventoryOpen = false;

    // ESC debouncing
    private boolean escGate = false;
    private float escCooldown = 0f;

    // Debug key
    // (WorldManager already has a toggle)

    // scratch
    private final Vector2 camCenterTmp = new Vector2();
    private final Vector2 playerPosTmp = new Vector2();

    // UI button style resources
    private final Array<Texture> uiTextures = new Array<>();
    private TextButtonStyle uiBtnStyle;

    private HitMarkerSystem hitMarkers;
    private final Vector3 mouseTmp = new Vector3();
    private final Vector2 mouseWorld = new Vector2();

    private com.rgs.runesurvivor.world.IslandRenderer island;

    // Save / autosave
    private com.rgs.runesurvivor.save.SaveManager saveManager;
    private float autosaveTimer = 0f;
    private static final float AUTOSAVE_MIN_INTERVAL = 0.25f; // avoid spamming prefs
    private PlayerSnapshot lastSaved;

    private static class PlayerSnapshot {
        float x,y,maxHp,currentHp,attack,critChance,critMult,moveSpeed;
        boolean sword;
    }

    private boolean exiting = false;

    // Pause icon UI
    private com.badlogic.gdx.scenes.scene2d.ui.ImageButton pauseBtn;
    private com.badlogic.gdx.graphics.Texture pauseIconUpTex, pauseIconDownTex;

    private com.rgs.runesurvivor.world.DashFxSystem dashFx;
    private float ghostSpawnTimer = 0f;
    private static final float GHOST_SPAWN_EVERY = 0.028f; // ~35 ghosts/sec while dashing

    private com.rgs.runesurvivor.ui.DeathOverlay deathOverlay;
    private boolean dead = false;

    private com.rgs.runesurvivor.ui.HealthPotionUI healthPotion;

    private com.rgs.runesurvivor.world.ResourceManager resourceManager;




    public GameScreen(RuneSurvivorGame game) {
        this.game = game;

        dashFx = new com.rgs.runesurvivor.world.DashFxSystem();

        // Stages (world + UI)
        worldStage = new Stage(new ExtendViewport(800, 480), game.batch);
        uiStage    = new Stage(new ExtendViewport(800, 480), game.batch);

        // Core systems
        worldManager    = new WorldManager();
        inputController = new InputController();

        // Build island with persisted seed (as you already do)
        saveManager = new com.rgs.runesurvivor.save.SaveManager();
        long islandSeed = saveManager.getOrCreateIslandSeed();

        island = new com.rgs.runesurvivor.world.IslandRenderer(
            512, 512, 40f, (int)(islandSeed & 0x7fffffff), worldManager.getWorld()
        );

        // resources (deterministic from the same seed)
        resourceManager = new com.rgs.runesurvivor.world.ResourceManager(
            island, islandSeed, worldManager.getWorld()
        );


        // Choose a safe land spawn near center
        com.badlogic.gdx.math.Vector2 spawn = island.findCenterLandSpawn();

        // Create player at safe land
        player = new Player(worldManager, spawn.x, spawn.y, inputController);
        player.setSwordEquipped(false);

        // Try to load player state; if loaded position is water, snap to nearest land
        if (saveManager.loadPlayer(player)) {
            float px = player.getBody().getPosition().x;
            float py = player.getBody().getPosition().y;
            if (island.isWaterWorld(px, py)) {
                com.badlogic.gdx.math.Vector2 safe = island.findNearestLand(px, py, 1024);
                player.setPosition(safe.x, safe.y);
            }
        } else {
            // ensure an initial save exists
            saveManager.savePlayer(player);
        }
        // Camera follow + snap
        cameraController = new com.rgs.runesurvivor.world.CameraController(worldStage.getCamera(), player);
        worldStage.getCamera().position.set(
            player.getBody().getPosition().x,
            player.getBody().getPosition().y,
            0f
        );
        worldStage.getCamera().update();

        // Try to load existing save; otherwise start at center
        if (!saveManager.loadPlayer(player)) {
            player.setPosition(0f, 0f); // your island center
        }

        // Enemies
        enemyManager = new EnemyManager(worldManager, island);

        // Overlays
        pauseOverlay = new PauseOverlay(
            () -> setPaused(false),
            this::requestExitToMainMenu
        );
        inventoryOverlay = new com.rgs.runesurvivor.ui.InventoryOverlay(
            player.isSwordEquipped(),
            equipped -> {
                player.setSwordEquipped(equipped);
                inventoryOverlay.setSwordEquipped(equipped);
                // instant save on equipment change
                saveManager.savePlayer(player);
                lastSaved = snapshotPlayer(player);
                autosaveTimer = 0f;
            }
        );

        deathOverlay = new com.rgs.runesurvivor.ui.DeathOverlay(new com.rgs.runesurvivor.ui.DeathOverlay.Listener() {
            @Override public void onRespawn() { respawnPlayer(); }
            @Override public void onMainMenu() { requestExitToMainMenu(); }
        });

        // HUD (Inventory button bottom-left)
        buildUi();

        // Hit markers & ShapeRenderer (HP bar)
        hitMarkers     = new com.rgs.runesurvivor.world.HitMarkerSystem();
        shapeRenderer  = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
    }

    private void buildUi() {
        uiBtnStyle = makeButtonStyle(new Color(0.15f, 0.15f, 0.18f, 0.9f));

        Table hud = new Table();
        hud.setFillParent(true);
        hud.bottom().left().pad(10f); // ⬅️ was top().left()
        uiStage.addActor(hud);

        TextButton inventoryBtn = new TextButton("Inventory", uiBtnStyle);
        inventoryBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                openInventory();
            }
        });
        hud.add(inventoryBtn).width(200f).height(56f);

        // ---- Health Potion button right of Inventory ----
        healthPotion = new com.rgs.runesurvivor.ui.HealthPotionUI(
            uiStage, "potion1.png", 10f,
            // Listener: only consume if allowed, then heal 20% max HP
            () -> {
                if (paused || dead || inventoryOpen) return false;
                if (player.getCurrentHp() <= 0f) return false; // already dead
                float max = player.getMaxHp();
                float cur = player.getCurrentHp();
                if (cur >= max) return false; // no effect at full health
                float heal = max * 0.20f;
                player.setCurrentHp(Math.min(max, cur + heal));

                // Optional: green heal pop
                try {
                    com.badlogic.gdx.math.Vector2 p = player.getBody().getPosition();
                    hitMarkers.spawn(p.x, p.y + 40f, "+" + Math.round(heal),
                        new com.badlogic.gdx.graphics.Color(0.25f, 1f, 0.35f, 1f), 0.9f);
                } catch (Throwable ignored) {}
                return true;
            }
        );

        // Add the button to the same row, to the right of Inventory
        hud.add(healthPotion.getButton()).size(64f);

        // ---- Pause icon (top-right) ----
        com.badlogic.gdx.scenes.scene2d.ui.Table tr = new com.badlogic.gdx.scenes.scene2d.ui.Table();
        tr.setFillParent(true);
        tr.top().right().pad(10f);
        uiStage.addActor(tr);

        pauseBtn = buildPauseButton();
        tr.add(pauseBtn).size(64f);
    }


    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputMultiplexer(uiStage, inputController));
    }

    // openInventory()
    private void openInventory() {
        if (inventoryOpen) return;
        inputController.reset(); // ← clear any held keys before focus change
        inventoryOpen = true;
        inventoryOverlay.setSwordEquipped(player.isSwordEquipped());
        inventoryOverlay.show();
        Gdx.input.setInputProcessor(new InputMultiplexer(uiStage, inventoryOverlay.getStage(), inputController));
        escGate = true; escCooldown = 0.12f;
    }

    // closeInventory()
    private void closeInventory() {
        if (!inventoryOpen) return;
        inventoryOpen = false;
        inventoryOverlay.hide();
        inputController.reset(); // ← clear any held keys after closing
        Gdx.input.setInputProcessor(new InputMultiplexer(uiStage, paused ? pauseOverlay.getStage() : inputController));
        escGate = true; escCooldown = 0.12f;
    }

    // setPaused(...)
    private void setPaused(boolean value) {
        if (paused == value) return;
        paused = value;

        inputController.reset(); // ← clear latched input on pause/unpause

        if (paused) {
            if (inventoryOpen) closeInventory();
            pauseOverlay.show();
            Gdx.input.setInputProcessor(new InputMultiplexer(uiStage, pauseOverlay.getStage()));
        } else {
            pauseOverlay.hide();
            Gdx.input.setInputProcessor(new InputMultiplexer(uiStage, inputController));
        }
        escGate = true; escCooldown = 0.12f;
    }

    private void togglePause() { setPaused(!paused); }

    @Override
    public void render(float delta) {
        // Hotkeys
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            worldManager.toggleDebug();
        }

        if (exiting) {
            Gdx.gl.glClearColor(0,0,0,1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            return;  // ← no stepping, no debugRender, no stage draws
        }

        // ESC gate/cooldown
        if (escGate && !Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) escGate = false;
        if (escCooldown > 0f) escCooldown -= delta;

        if (!escGate && escCooldown <= 0f && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (inventoryOpen) {
                // ESC closes inventory first
                closeInventory();
            } else {
                togglePause();
            }
            escGate = true; escCooldown = 0.12f;
        }

        if (inventoryOpen && !inventoryOverlay.isVisible()) {
            closeInventory();  // updates flag + restores input routing
        }

        // Simulation pauses if either paused or inventoryOpen
        boolean simulate = !paused;
        if (simulate) {
            worldManager.step();
            player.update();
            cameraController.update();

            // Detect death
            if (!dead && player.getCurrentHp() <= 0f) {
                onPlayerDeath();
            }

            camCenterTmp.set(worldStage.getCamera().position.x, worldStage.getCamera().position.y);
            playerPosTmp.set(player.getBody().getPosition());
            float vw = worldStage.getViewport().getWorldWidth();
            float vh = worldStage.getViewport().getWorldHeight();
            enemyManager.update(delta, camCenterTmp, playerPosTmp, vw, vh, player, hitMarkers);

            dashFx.update(delta);

            // spawn ghosts while dashing
            if (player.isDashing()) {
                ghostSpawnTimer += delta;
                while (ghostSpawnTimer >= GHOST_SPAWN_EVERY) {
                    ghostSpawnTimer -= GHOST_SPAWN_EVERY;

                    // center = body, size = current player size, facing => flipX
                    float cx = player.getBody().getPosition().x;
                    float cy = player.getBody().getPosition().y;
                    dashFx.spawnGhost(cx, cy, player.getWidth(), player.getHeight(), 0f, !player.isFacingRight());
                }
            }

            boolean onePressed = !paused && !dead && !inventoryOpen
                && Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_1);
            if (onePressed && healthPotion != null) {
                healthPotion.requestUse();
            }
        }

        autosaveTimer += delta;

        // Build current snapshot
        PlayerSnapshot cur = snapshotPlayer(player);

        // Save if anything changed and we’re beyond the debounce window
        if (differs(cur, lastSaved) && autosaveTimer >= AUTOSAVE_MIN_INTERVAL) {
            saveManager.savePlayer(player);
            lastSaved = cur;
            autosaveTimer = 0f;
        }

        // Draw
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        game.batch.setProjectionMatrix(worldStage.getCamera().combined);
        game.batch.begin();

        // 1) Terrain first
        island.render(game.batch);

        // 2) Characters (draw these UNDER resource nodes)
        enemyManager.render(game.batch);
        player.renderSword(game.batch);          // (ok if this ends up under trees too)
        dashFx.renderGhosts(game.batch, player.getTexture());
        player.render(game.batch);

        // 3) Resource nodes ON TOP of the player
        if (resourceManager != null) resourceManager.render(game.batch);

        // 4) Overlays that should stay above everything
        hitMarkers.render(game.batch);

        game.batch.end();


        // ---- FILLED shapes (BEGIN before calling Enemy.renderAttack!) ----
        shapeRenderer.setProjectionMatrix(worldStage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Enemy attack telegraphs (windup/strike wedges)
        for (com.rgs.runesurvivor.entities.Enemy e : enemyManager.getEnemies()) {
            e.renderAttack(shapeRenderer);  // <- MUST be inside begin/end
        }

        // Player HP bar
        player.renderHpBar(shapeRenderer);

        if (healthPotion != null) healthPotion.update(delta);

        shapeRenderer.end();

        worldManager.debugRender(worldStage.getCamera());

        uiStage.act(delta);
        uiStage.draw();

        if (!paused) {
            worldManager.step();
            player.update();
            cameraController.update();

            float vw = worldStage.getViewport().getWorldWidth();
            float vh = worldStage.getViewport().getWorldHeight();
            enemyManager.update(delta, camCenterTmp, playerPosTmp, vw, vh, player, hitMarkers);

            // Mouse world position
            mouseTmp.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
            worldStage.getCamera().unproject(mouseTmp);
            mouseWorld.set(mouseTmp.x, mouseTmp.y);

            boolean dashPressed = !paused && !inventoryOpen && Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
            if (dashPressed) {
                if (player.tryStartDash(mouseWorld)) {
                    // spawn one shockwave at dash start
                    com.badlogic.gdx.math.Vector2 p = player.getBody().getPosition();
                    dashFx.spawnWave(p.x, p.y, 12f, 220f); // small -> big ring
                    ghostSpawnTimer = 0f; // reset ghost cadence
                }
            }

            // Left click attack (disable while inventory open)
            boolean atkPressed = !inventoryOpen
                && player.isSwordEquipped()
                && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);

            boolean atkHeld = !inventoryOpen
                && player.isSwordEquipped()
                && Gdx.input.isButtonPressed(Input.Buttons.LEFT);

            player.updateCombat(delta, mouseWorld, atkPressed, atkHeld, enemyManager, hitMarkers);


            // Hit markers
            hitMarkers.update(delta);
        }

        if (paused) {
            pauseOverlay.actAndDraw(delta);
        }
        if (inventoryOpen) {
            inventoryOverlay.actAndDraw(delta);
        }

        if (deathOverlay != null && deathOverlay.isVisible()) {
            deathOverlay.render(delta);
        }

        // UI cooldown dial above the potion icon
        shapeRenderer.setProjectionMatrix(uiStage.getCamera().combined);
        shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        shapeRenderer.end();
    }

    @Override
    public void resize(int width, int height) {
        worldStage.getViewport().update(width, height, true);
        uiStage.getViewport().update(width, height, true);
        pauseOverlay.resize(width, height);
        inventoryOverlay.resize(width, height);
        if (deathOverlay != null) deathOverlay.resize(width, height);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide()    { if (saveManager != null) saveManager.savePlayer(player); }

    @Override
    public void dispose() {

        // 1) tear down systems that DESTROY BODIES/ FIXTURES
        //    (must happen while World is still alive)
        if (enemyManager != null) {
            enemyManager.dispose();   // destroys enemy bodies
            enemyManager = null;
        }
        if (island != null) {
            island.dispose();         // destroys water-edge bodies
            island = null;
        }
        // Player: DO NOT destroy the player body explicitly;
        // letting world.dispose() clean it up is fine. Only dispose textures.
        if (player != null) {
            player.dispose();
            player = null;
        }

        // 2) now it's safe to dispose the World itself
        if (worldManager != null) {
            worldManager.setDebug(false);
            worldManager.dispose();   // disposes Box2DDebugRenderer and the World
            worldManager = null;
        }

        // 3) UI / render-only stuff
        if (uiStage != null) { uiStage.dispose(); uiStage = null; }
        if (worldStage != null) { worldStage.dispose(); worldStage = null; }
        if (pauseOverlay != null) { pauseOverlay.dispose(); pauseOverlay = null; }
        if (inventoryOverlay != null) { inventoryOverlay.dispose(); inventoryOverlay = null; }
        if (hitMarkers != null) { hitMarkers.dispose(); hitMarkers = null; }
        if (shapeRenderer != null) { shapeRenderer.dispose(); shapeRenderer = null; }

        // 4) anything else (textures, button skins you track in arrays, etc.)
        // uiTextures, etc…

        if (healthPotion != null) { healthPotion.dispose(); healthPotion = null; }

        if (resourceManager != null) { resourceManager.dispose(); resourceManager = null; }
    }

    // ---- small style helper for the HUD button ----
    private TextButtonStyle makeButtonStyle(Color base) {
        TextButtonStyle s = new TextButtonStyle();
        s.font = new BitmapFont(); // small standalone font for HUD button
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
        uiTextures.add(tex);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return tex;
    }

    private Color scale(Color c, float mul) {
        return new Color(Math.min(c.r*mul,1f), Math.min(c.g*mul,1f), Math.min(c.b*mul,1f), c.a);
    }

    private PlayerSnapshot snapshotPlayer(Player p) {
        PlayerSnapshot s = new PlayerSnapshot();
        s.x = p.getBody().getPosition().x;
        s.y = p.getBody().getPosition().y;
        s.maxHp = p.getMaxHp();
        s.currentHp = p.getCurrentHp();
        s.attack = p.getAttack();
        s.critChance = p.getCritChance();
        s.critMult = p.getCritMultiplier();
        s.moveSpeed = p.getMoveSpeed();
        s.sword = p.isSwordEquipped();
        return s;
    }

    private boolean differs(PlayerSnapshot a, PlayerSnapshot b) {
        if (b == null) return true;
        final float E = 0.01f;
        return Math.abs(a.x - b.x) > E
            || Math.abs(a.y - b.y) > E
            || Math.abs(a.maxHp - b.maxHp) > E
            || Math.abs(a.currentHp - b.currentHp) > E
            || Math.abs(a.attack - b.attack) > E
            || Math.abs(a.critChance - b.critChance) > E
            || Math.abs(a.critMult - b.critMult) > E
            || Math.abs(a.moveSpeed - b.moveSpeed) > E
            || a.sword != b.sword;
    }

    private void requestExitToMainMenu() {
        if (exiting) return;
        exiting = true;

        // Save before teardown
        if (saveManager != null) saveManager.savePlayer(player);

        // Stop new events
        Gdx.input.setInputProcessor(null);

        // Absolutely no more Box2D debug draw
        worldManager.setDebug(false);

        // Defer screen change + dispose until after this frame
        Gdx.app.postRunnable(() -> {
            game.setScreen(new MainMenuScreen(game));
            dispose();
        });
    }

    private Texture makePauseIconTex(int size, com.badlogic.gdx.graphics.Color bg, com.badlogic.gdx.graphics.Color bar) {
        com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(size, size, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        pm.setColor(0,0,0,0); pm.fill();

        // circular background
        pm.setColor(bg);
        pm.fillCircle(size/2, size/2, size/2);

        // two pause bars
        int barW = Math.round(size * 0.18f);
        int barH = Math.round(size * 0.55f);
        int gap  = Math.round(size * 0.14f);
        int cx = size/2, cy = size/2;
        int x1 = cx - gap/2 - barW;
        int x2 = cx + gap/2;
        int y  = cy - barH/2;

        pm.setColor(bar);
        pm.fillRectangle(x1, y, barW, barH);
        pm.fillRectangle(x2, y, barW, barH);

        com.badlogic.gdx.graphics.Texture tex = new com.badlogic.gdx.graphics.Texture(pm);
        tex.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        pm.dispose();
        return tex;
    }

    private com.badlogic.gdx.scenes.scene2d.ui.ImageButton buildPauseButton() {
        // up/pressed variants
        pauseIconUpTex   = makePauseIconTex(96, new com.badlogic.gdx.graphics.Color(0f,0f,0f,0.55f),
            new com.badlogic.gdx.graphics.Color(1f,1f,1f,1f));
        pauseIconDownTex = makePauseIconTex(96, new com.badlogic.gdx.graphics.Color(0f,0f,0f,0.75f),
            new com.badlogic.gdx.graphics.Color(0.92f,0.92f,0.92f,1f));

        com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable up   =
            new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(pauseIconUpTex));
        com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable down =
            new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(pauseIconDownTex));

        com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle style =
            new com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle();
        style.imageUp = up;
        style.imageDown = down;

        com.badlogic.gdx.scenes.scene2d.ui.ImageButton b = new com.badlogic.gdx.scenes.scene2d.ui.ImageButton(style);
        b.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                setPaused(true); // open the pause overlay you already have
            }
        });
        return b;
    }

    private void onPlayerDeath() {
        if (dead) return;
        dead = true;

        // Freeze gameplay like pause, hide pause button/overlay
        setPaused(true);
        if (pauseOverlay != null) pauseOverlay.hide();
        if (pauseBtn != null) pauseBtn.setVisible(false);
        if (inventoryOverlay != null) inventoryOverlay.hide();
        deathOverlay.show();
    }

    private void respawnPlayer() {
        // Find a safe land point near island center
        com.badlogic.gdx.math.Vector2 spawn = island.findCenterLandSpawn();

        // Reset player
        player.setPosition(spawn.x, spawn.y);
        player.setCurrentHp(player.getMaxHp());
        try { player.setStamina(player.getMaxStamina()); } catch (Throwable ignored) {}

        // (Optional) clear nearby enemies so you don't get chain-killed immediately
        if (enemyManager != null) enemyManager.despawnWithinRadius(spawn, 450f);

        // Unfreeze
        dead = false;
        setPaused(false);
        if (pauseBtn != null) pauseBtn.setVisible(true);
        deathOverlay.hide();

        // Restore input to gameplay/UI stage
        Gdx.input.setInputProcessor(uiStage);

        // Save new state
        if (saveManager != null) {
            saveManager.savePlayer(player);
        }
    }

}
