package com.rgs.runesurvivor.world;

import com.badlogic.gdx.graphics.Camera;
import com.rgs.runesurvivor.entities.Player;

public class CameraController {
    private Camera camera;
    private Player player;

    public CameraController(Camera camera, Player player) {
        this.camera = camera;
        this.player = player;
    }

    public void update() {
        camera.position.set(player.getBody().getPosition().x,
            player.getBody().getPosition().y, 0);
        camera.update();
    }
}
