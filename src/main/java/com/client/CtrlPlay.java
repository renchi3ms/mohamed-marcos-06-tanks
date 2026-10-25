package com.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import com.shared.BulletData;
import com.shared.TankData;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Controlador de la vista de joc (Canvas on es dibuixa l'estat del servidor).
 *
 * El client nomes es un "terminal prim": captura les intencions del jugador
 * (tecles premudes, punter i dispar) i les envia al servidor com a missatges
 * "clientInput". Mai no calcula cap moviment: qui decideix on va cada tanc
 * es sempre el servidor.
 */
public class CtrlPlay {

    @FXML public Label title;
    @FXML public Canvas canvas;

    /** Tecles de moviment actualment premudes: "UP", "DOWN", "LEFT", "RIGHT". */
    private final Set<String> keys = new HashSet<>();

    /** Punter sobre el tauler (coordenades del Canvas). */
    private double aimX = 0;
    private double aimY = 0;

    /** Botó esquerre del ratoli premut = foc automatic. */
    private boolean shoot = false;

    private static final double TANK_RADIUS = 25;
    private static final double BULLET_RADIUS = 5;

    /** Posicio del darrer frame de cada bala viva (id -> {x, y}), per detectar les que moren. */
    private final Map<String, double[]> prevBulletPos = new HashMap<>();

    /** Estat del tanc del frame anterior (id -> dades), per detectar els que moren. */
    private final Map<String, TankData> prevTanks = new HashMap<>();

    @FXML
    private void initialize() {

        // Les tecles es capturen a nivell d'escena (el Canvas no te focus).
        canvas.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                installSceneKeyListeners(scene);
            }
        });

        canvas.setOnMouseMoved(e -> updateAim(e.getX(), e.getY()));
        canvas.setOnMouseDragged(e -> updateAim(e.getX(), e.getY()));

        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                shoot = true;
                sendInput();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                shoot = false;
                sendInput();
            }
        });

        // Bucle de renderitzat: cada frame es pinta l'ultim estat rebut del servidor.
        AnimationTimer renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                render();
            }
        };
        renderTimer.start();
    }

    /** Pinta l'estat del servidor (obstacles, tancs, bales) sobre el Canvas. */
    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (Main.obstacles != null) {
            g.setFill(Color.DARKGRAY);
            for (double[] o : Main.obstacles) {
                g.fillRect(o[0], o[1], o[2], o[3]);
            }
        }

        if (Main.tanks != null) {
            for (TankData tank : Main.tanks) {
                if (!tank.alive) continue;

                Color tankColor = tank.ownerName.equals(Main.clientName)
                        ? Color.FORESTGREEN : Color.FIREBRICK;

                // Cos: caterpillers i casc, girats segons la direccio de marxa.
                g.save();
                g.translate(tank.x, tank.y);
                g.rotate(tank.angle);
                g.setFill(Color.DARKSLATEGRAY);
                g.fillRect(-18, -17, 36, 6);
                g.fillRect(-18, 11, 36, 6);
                g.setFill(tankColor);
                g.fillRect(-16, -11, 32, 22);
                g.restore();

                // Torreta: cano i cupula, girats segons l'apuntament.
                g.save();
                g.translate(tank.x, tank.y);
                g.rotate(tank.turretAngle);
                g.setFill(tankColor.darker());
                g.fillRect(0, -3, 32, 6);
                g.fillOval(-9, -9, 18, 18);
                g.restore();

                // Nom del jugador sobre el tanc
                g.setFill(Color.WHITE);
                g.fillText(tank.ownerName, tank.x - 20, tank.y - TANK_RADIUS - 5);
            }
        }

        if (Main.bullets != null) {
            g.setFill(Color.GOLD);
            for (BulletData b : Main.bullets) {
                g.fillOval(b.x - BULLET_RADIUS, b.y - BULLET_RADIUS,
                           BULLET_RADIUS * 2, BULLET_RADIUS * 2);
            }
        }

        drawHud(g);

        spawnExplosions();
    }

    /**
     * Pinta el HUD a sobre del tauler: nom, vides i projectils de cada
     * jugador, mas l'escenari actual. Les dades es llegeixen de l'ultim
     * estat rebut del servidor (Main.tanks / Main.bullets / Main.scenario).
     */
    private void drawHud(GraphicsContext g) {
        if (Main.tanks == null || Main.tanks.isEmpty()) return;

        Font hudFont = Font.font("Monospaced", FontWeight.BOLD, 13);
        g.setFont(hudFont);
        hudMeasure.setFont(hudFont);

        TankData local = null;
        TankData enemy = null;
        for (TankData t : Main.tanks) {
            if (t.ownerName.equals(Main.clientName)) local = t; else enemy = t;
        }

        double boxH = 42;
        double top = 8;

        if (local != null) {
            String l1 = hudLine1(local);
            String l2 = hudLine2(local);
            double w = Math.max(hudTextWidth(l1), hudTextWidth(l2)) + 16;

            g.setFill(Color.rgb(0, 0, 0, 0.55));
            g.fillRoundRect(8, top, w, boxH, 8, 8);
            g.setFill(Color.WHITE);
            g.fillText(l1, 16, top + 17);
            g.setFill(Color.RED);
            g.fillText(hudHearts(local), 16 + hudTextWidth(local.ownerName + " "), top + 17);
            g.setFill(Color.WHITE);
            g.fillText(l2, 16, top + 34);
        }

        if (enemy != null) {
            String l1 = hudLine1(enemy);
            String l2 = hudLine2(enemy);
            double textW = Math.max(hudTextWidth(l1), hudTextWidth(l2));

            g.setFill(Color.rgb(0, 0, 0, 0.55));
            g.fillRoundRect(canvas.getWidth() - 8 - (textW + 16), top, textW + 16, boxH, 8, 8);
            g.setFill(Color.WHITE);
            g.fillText(l1, canvas.getWidth() - 16 - textW, top + 17);
            g.setFill(Color.RED);
            g.fillText(hudHearts(enemy), canvas.getWidth() - 16 - textW + hudTextWidth(enemy.ownerName + " "), top + 17);
            g.setFill(Color.WHITE);
            g.fillText(l2, canvas.getWidth() - 16 - textW, top + 34);
        }

        if (Main.scenario != null && !Main.scenario.isEmpty()) {
            String s = "Escenari: " + Main.scenario;
            double w = hudTextWidth(s) + 16;
            double bottom = canvas.getHeight() - 30;

            g.setFill(Color.rgb(0, 0, 0, 0.55));
            g.fillRoundRect((canvas.getWidth() - w) / 2, bottom, w, 22, 8, 8);
            g.setFill(Color.WHITE);
            g.fillText(s, (canvas.getWidth() - hudTextWidth(s)) / 2, bottom + 15);
        }
    }

    /** Node Text reciclat per mesurar l'ample del text del HUD. */
    private final Text hudMeasure = new Text();

    private double hudTextWidth(String s) {
        hudMeasure.setText(s);
        return hudMeasure.getLayoutBounds().getWidth();
    }

    private String hudLine1(TankData tank) {
        return tank.ownerName + " " + hudHearts(tank);
    }

    private String hudHearts(TankData tank) {
        return "♥".repeat(Math.max(0, tank.lives));
    }

    private String hudLine2(TankData tank) {
        long active = 0;
        if (Main.bullets != null) {
            active = Main.bullets.stream().filter(b -> b.ownerId.equals(tank.id)).count();
        }
        return "Projectils: " + active;
    }

    /**
     * Compara l'estat actual amb el del frame anterior i fa néixer una
     * explosio per cada bala o tanc que s'ha destruit entremig.
     */
    private void spawnExplosions() {
        if (Main.explosionLayer == null) return;

        if (Main.bullets != null) {
            Set<String> ids = new HashSet<>();
            for (BulletData b : Main.bullets) ids.add(b.id);
            for (Map.Entry<String, double[]> e : prevBulletPos.entrySet()) {
                if (!ids.contains(e.getKey())) {
                    double[] p = e.getValue();
                    Main.explosionLayer.spawn(p[0], p[1], 14);
                }
            }
            prevBulletPos.clear();
            for (BulletData b : Main.bullets) prevBulletPos.put(b.id, new double[] {b.x, b.y});
        } else {
            prevBulletPos.clear();
        }

        if (Main.tanks != null) {
            for (TankData t : Main.tanks) {
                TankData before = prevTanks.get(t.id);
                if (before != null && before.alive && !t.alive) {
                    Main.explosionLayer.spawn(t.x, t.y, 40);
                }
            }
            prevTanks.clear();
            for (TankData t : Main.tanks) prevTanks.put(t.id, t);
        } else {
            prevTanks.clear();
        }
    }

    /** Reinicia els efectes visuals (cridat en tornar al lobby). */
    public void resetEffects() {
        prevBulletPos.clear();
        prevTanks.clear();
        if (Main.explosionLayer != null) Main.explosionLayer.clear();
    }

    private void installSceneKeyListeners(Scene scene) {
        scene.setOnKeyPressed(e -> {
            String key = mapKey(e.getCode());
            if (key != null && keys.add(key)) {
                sendInput();
            }
        });

        scene.setOnKeyReleased(e -> {
            String key = mapKey(e.getCode());
            if (key != null && keys.remove(key)) {
                sendInput();
            }
        });
    }

    private static String mapKey(KeyCode code) {
        switch (code) {
            case W, UP -> { return "UP"; }
            case S, DOWN -> { return "DOWN"; }
            case A, LEFT -> { return "LEFT"; }
            case D, RIGHT -> { return "RIGHT"; }
            default -> { return null; }
        }
    }

    /**
     * Actualitza el punter i reenvia l'input nomes si el moviment es
     * prou significatiu (evita inundar el socket amb micro-moviments).
     */
    private void updateAim(double x, double y) {
        double dx = x - aimX;
        double dy = y - aimY;
        if (dx * dx + dy * dy >= 9) { // llindar de 3 px
            aimX = x;
            aimY = y;
            sendInput();
        }
    }

    /** Envia les intencions actuals al servidor (mai posicions absolutes). */
    /** Hook de prova: foc automatic cap al tanc enemic (per smoke tests sense ratoli). */
    private boolean autoShootStarted = false;

    public void startAutoShoot() {
        if (autoShootStarted) return;
        autoShootStarted = true;

        Thread t = new Thread(() -> {
            while (true) {
                try { Thread.sleep(400); } catch (InterruptedException e) { return; }
                if (Main.tanks != null) {
                    for (TankData tank : Main.tanks) {
                        if (tank.ownerName.equals(Main.clientName)) {
                            aimX = (tank.x < 300) ? 510 : 90;
                            aimY = 210;
                        }
                    }
                }
                shoot = !shoot;
                sendInput();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void sendInput() {
        if (Main.wsClient == null) return;

        JSONObject msg = new JSONObject();
        msg.put("type", "clientInput");
        msg.put("keys", new JSONArray(keys.toArray(new String[0])));
        msg.put("aimX", aimX);
        msg.put("aimY", aimY);
        msg.put("shoot", shoot);

        Main.wsClient.safeSend(msg.toString());
    }
}
