package com.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

import org.json.JSONArray;
import org.json.JSONObject;

import com.shared.BulletData;
import com.shared.HelperCollisions;
import com.shared.HelperMath;
import com.shared.InputData;
import com.shared.TankData;

/**
 * Estat autoritatiu de la partida (servidor).
 *
 * El servidor es l'unica font de veritat: els clients nomes envien
 * intencions (InputData) i reben l'estat complet a cada tick.
 *
 * Tots els metodes es criden des del fil del ticker (o del fil de lectura
 * WebSocket amb el monitor de Game agafat), per aixto tot l'estat es protegeix
 * amb 'synchronized'.
 */
public class Game {

    public static final double TANK_RADIUS = 25;
    public static final double BULLET_RADIUS = 5;
    public static final double TANK_SPEED = 140;    // px/segon
    public static final double BULLET_SPEED = 360;  // px/segon
    public static final int MAX_BULLETS = 3;        // bales actives per jugador
    public static final int START_LIVES = 3;

    private static final double WALL = 20;          // gruix de les parets del voltant
    // Divisor de temps (no distancia): cada tick es divideix en 10 subpassos
    // petits perque cap bala no es "salte" cap col·lisio en un sol salt.
    private static final int PHYSICS_PRECISION = 10;

    private final double width;
    private final double height;

    private final Map<String, TankData> tanks = new HashMap<>();      // clau = ownerName
    private final List<BulletData> bullets = new ArrayList<>();
    private final Map<String, InputData> inputs = new HashMap<>();    // clau = ownerName
    private final List<Rectangle2D> obstacles = new ArrayList<>();

    private boolean over = false;
    private String winner = "";
    private String loser = "";
    private int bulletCounter = 0;

    public final String scenario;

    public Game(String scenario, double width, double height) {
        this.width = width;
        this.height = height;
        this.scenario = scenario;
        buildScenario(scenario);
    }

    // ----------------- Escenaris -----------------

    private void buildScenario(String scenario) {
        obstacles.clear();

        // Paret del voltant
        obstacles.add(new Rectangle2D(0, 0, width, WALL));                    // dalt
        obstacles.add(new Rectangle2D(0, height - WALL, width, WALL));        // baix
        obstacles.add(new Rectangle2D(0, 0, WALL, height));                   // esquerra
        obstacles.add(new Rectangle2D(width - WALL, 0, WALL, height));        // dreta

        switch (scenario) {
            case "corridor" -> {
                // Passadissos: dos murs horitzontals amb un buit al mig
                obstacles.add(new Rectangle2D(150, 140, 140, 20));
                obstacles.add(new Rectangle2D(310, 140, 140, 20));
                obstacles.add(new Rectangle2D(150, 260, 140, 20));
                obstacles.add(new Rectangle2D(310, 260, 140, 20));
            }
            case "maze" -> {
                // Laberint: murs verticals alterns que formen un zigzag
                obstacles.add(new Rectangle2D(150, 20, 20, 140));
                obstacles.add(new Rectangle2D(290, height - 180, 20, 140));
                obstacles.add(new Rectangle2D(430, 20, 20, 140));
            }
            default -> {
                // Arena: dos obstacles aleatoris (les posicions no se solapen)
                List<Rectangle2D> options = new ArrayList<>(List.of(
                    new Rectangle2D(260, 100, 60, 100),
                    new Rectangle2D(260, 300, 60, 100),
                    new Rectangle2D(370, 170, 60, 160),
                    new Rectangle2D(480, 100, 60, 100),
                    new Rectangle2D(480, 300, 60, 100)
                ));
                Collections.shuffle(options);
                obstacles.add(options.get(0));
                obstacles.add(options.get(1));
            }
        }
    }

    // ----------------- Jugadors -----------------

    /** Crea el tanc del jugador. El primer apareix a l'esquerra, el segon a la dreta. */
    public synchronized void addPlayer(String name) {
        if (tanks.containsKey(name)) return;

        boolean leftSide = tanks.size() == 0;
        double x = leftSide ? 90 : width - 90;
        double angle = leftSide ? 0 : 180;

        tanks.put(name, new TankData("T" + name, name, x, height / 2, angle, angle, START_LIVES, true));
        inputs.put(name, new InputData());
    }

    public synchronized void removePlayer(String name) {
        tanks.remove(name);
        inputs.remove(name);
    }

    /** Registra l'ultim estat d'input del client (no el processa). */
    public synchronized void setInput(String name, InputData input) {
        inputs.put(name, input);
    }

    // ----------------- Tick -----------------

    /** Avanca la simulacio 'dt' segons. Cridat pel ticker del servidor a 30 fps. */
    public synchronized void tick(double dt) {
        if (over || tanks.size() < 2) return;

        // Passos petits per no "saltar-se" col·lisions (com el demo local)
        double stepDt = dt / PHYSICS_PRECISION;
        for (int step = 0; step < PHYSICS_PRECISION; step++) {
            moveTanks(stepDt);
            moveBullets(stepDt);
        }

        checkGameOver();    }

    private void moveTanks(double dt) {
        for (TankData tank : tanks.values()) {
            if (!tank.alive) continue;

            InputData input = inputs.get(tank.ownerName);
            if (input == null) continue;

            // Direccio a partir de les tecles premudes
            double dx = 0, dy = 0;
            if (input.keys.contains("LEFT")) dx -= 1;
            if (input.keys.contains("RIGHT")) dx += 1;
            if (input.keys.contains("UP")) dy -= 1;
            if (input.keys.contains("DOWN")) dy += 1;

            Point2D position = new Point2D(tank.x, tank.y);

            if (dx != 0 || dy != 0) {
                Point2D direction = new Point2D(dx, dy);
                tank.angle = HelperMath.angleForMovement(direction);

                Point2D next = HelperMath.positionAtDistance(position, tank.angle, TANK_SPEED * dt);

                // Si el moviment complet xoca, provar nomes moure en X o en Y:
                // aixo fa que el tanc llisqui vora la paret en lloc d'enganxar-s'hi.
                Point2D onlyX = new Point2D(next.getX(), position.getY());
                Point2D onlyY = new Point2D(position.getX(), next.getY());

                if (!isBlocked(next, tank)) position = next;
                else if (!isBlocked(onlyX, tank)) position = onlyX;
                else if (!isBlocked(onlyY, tank)) position = onlyY;
            }

            // El canó apunta on hi ha el cursor (input.aim)
            tank.turretAngle = HelperMath.angleToTarget(position, new Point2D(input.aimX, input.aimY));
            tank.x = position.getX();
            tank.y = position.getY();

            // Disparar: un sol tir per missatge clientInput amb shoot=true (focus per clic).
            if (input.shoot && !input.shootFired) {
                fireBullet(tank);
                input.shootFired = true;
            }
        }
    }

    private void fireBullet(TankData tank) {
        long active = bullets.stream().filter(b -> b.ownerId.equals(tank.id)).count();
        if (active >= MAX_BULLETS) return;

        bullets.add(new BulletData("B" + (bulletCounter++), tank.id,
            tank.x, tank.y, tank.turretAngle, 0));
    }

    private void moveBullets(double dt) {
        List<BulletData> toRemove = new ArrayList<>();

        for (BulletData bullet : bullets) {
            if (bullet.bounces >= 2) {
                toRemove.add(bullet);
                continue;
            }

            Point2D current = new Point2D(bullet.x, bullet.y);
            Point2D next = HelperMath.positionAtDistance(current, bullet.angle, BULLET_SPEED * dt);

            // 1. Paret o obstacle: el primer cop rebota, el segon desapareix.
            if (blockedForBullet(next)) {
                if (bullet.bounces >= 1) {
                    toRemove.add(bullet);
                } else {
                    // Paret vertical = xoca nomes en X; horitzontal = nomes en Y.
                    boolean invertX = blockedForBullet(new Point2D(next.getX(), current.getY()));
                    boolean invertY = blockedForBullet(new Point2D(current.getX(), next.getY()));
                    if (!invertX && !invertY) {
                        // Ha tocat una cantonada: torna enrere.
                        invertX = true;
                        invertY = true;
                    }
                    bullet.angle = bounceAngle(bullet.angle, invertX, invertY);
                    bullet.bounces++;
                }
                continue; // La bala no avanca aquest pas.
            }
            bullet.x = next.getX();
            bullet.y = next.getY();

            // 2. Tancs: els tancs enemics sempre; el del seu amo nomes si ja ha rebotat.
            for (TankData tank : tanks.values()) {
                if (!tank.alive) continue;
                boolean ownTank = tank.id.equals(bullet.ownerId);
                if (ownTank && bullet.bounces == 0) continue;

                if (HelperCollisions.circlesOverlap(new Point2D(bullet.x, bullet.y), BULLET_RADIUS,
                        new Point2D(tank.x, tank.y), TANK_RADIUS)) {
                    tank.lives--;
                    if (tank.lives <= 0) tank.alive = false;
                    toRemove.add(bullet);
                    break;
                }
            }
        }

        // 3. Dues bales d'amos diferents que es toquen desapareixen totes dues.
        for (int i = 0; i < bullets.size(); i++) {
            for (int j = i + 1; j < bullets.size(); j++) {
                BulletData a = bullets.get(i);
                BulletData b = bullets.get(j);
                if (a.ownerId.equals(b.ownerId)) continue;
                if (HelperCollisions.circlesOverlap(new Point2D(a.x, a.y), BULLET_RADIUS,
                        new Point2D(b.x, b.y), BULLET_RADIUS)) {
                    toRemove.add(a);
                    toRemove.add(b);
                }
            }
        }

        bullets.removeAll(toRemove);
    }

    private static double bounceAngle(double angle, boolean invertX, boolean invertY) {
        Point2D direction = HelperMath.directionForAngle(angle);
        double dx = invertX ? -direction.getX() : direction.getX();
        double dy = invertY ? -direction.getY() : direction.getY();
        return HelperMath.angleForMovement(new Point2D(dx, dy));
    }

    private void checkGameOver() {
        if (over) return;
        for (TankData tank : tanks.values()) {
            if (tank.alive) continue;
            over = true;
            loser = tank.ownerName;
            for (TankData other : tanks.values()) {
                if (other.alive) winner = other.ownerName;
            }
            return;
        }
    }

    // ----------------- Consultes -----------------

    private boolean isBlocked(Point2D position, TankData self) {
        if (blockedForTank(position)) return true;
        for (TankData other : tanks.values()) {
            if (other != self && other.alive
                    && HelperCollisions.circlesOverlap(position, TANK_RADIUS,
                        new Point2D(other.x, other.y), TANK_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    private boolean blockedForBullet(Point2D position) {
        for (Rectangle2D obstacle : obstacles) {
            if (HelperCollisions.circleOverlapsRectangle(position, BULLET_RADIUS, obstacle)) return true;
        }
        return false;
    }

    private boolean blockedForTank(Point2D position) {
        for (Rectangle2D obstacle : obstacles) {
            if (HelperCollisions.circleOverlapsRectangle(position, TANK_RADIUS, obstacle)) return true;
        }
        return false;
    }

    public synchronized boolean isOver() {
        return over;
    }

    public synchronized String winner() {
        return winner;
    }

    public synchronized String loser() {
        return loser;
    }

    public synchronized boolean hasPlayer(String name) {
        return tanks.containsKey(name);
    }

    /** Un jugador marxa: l'altre guanya per abandonament. */
    public synchronized void forfeit(String loserName) {
        if (over) return;
        over = true;
        loser = loserName;
        for (TankData other : tanks.values()) {
            if (!other.ownerName.equals(loserName)) winner = other.ownerName;
        }
    }

    // ----------------- Serialitzacio (estat -> JSON per al broadcast) -----------------

    public synchronized JSONArray clientsJson() {
        JSONArray arr = new JSONArray();
        for (TankData tank : tanks.values()) {
            arr.put(tank.ownerName);
        }
        return arr;
    }

    public synchronized JSONArray tanksJson() {
        JSONArray arr = new JSONArray();
        for (TankData tank : tanks.values()) {
            arr.put(tank.toJSON());
        }
        return arr;
    }

    public synchronized JSONArray bulletsJson() {
        JSONArray arr = new JSONArray();
        for (BulletData bullet : bullets) {
            arr.put(bullet.toJSON());
        }
        return arr;
    }

    public synchronized JSONArray obstaclesJson() {
        JSONArray arr = new JSONArray();
        for (Rectangle2D obstacle : obstacles) {
            arr.put(new JSONObject()
                .put("x", obstacle.getMinX())
                .put("y", obstacle.getMinY())
                .put("w", obstacle.getWidth())
                .put("h", obstacle.getHeight()));
        }
        return arr;
    }
}
