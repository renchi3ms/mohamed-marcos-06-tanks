package com.shared;

import org.json.JSONObject;

/**
 * Estat autoritatiu d'un tanc (S -> C). El servidor en es l'unica font:
 * el client nomes rep i dibuixa, mai no envia posicions.
 */
public class TankData {

    public String id;
    public String ownerName;
    public double x;
    public double y;
    public double angle;        // angle del casc, en graus
    public double turretAngle;  // angle del cano, en graus
    public int lives;
    public boolean alive;

    public TankData() { }

    public TankData(String id, String ownerName, double x, double y,
                    double angle, double turretAngle, int lives, boolean alive) {
        this.id = id;
        this.ownerName = ownerName;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.turretAngle = turretAngle;
        this.lives = lives;
        this.alive = alive;
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }

    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("ownerName", ownerName);
        obj.put("x", x);
        obj.put("y", y);
        obj.put("angle", angle);
        obj.put("turretAngle", turretAngle);
        obj.put("lives", lives);
        obj.put("alive", alive);
        return obj;
    }

    public static TankData fromJSON(JSONObject obj) {
        return new TankData(
            obj.optString("id", null),
            obj.optString("ownerName", ""),
            obj.optDouble("x", 0),
            obj.optDouble("y", 0),
            obj.optDouble("angle", 0),
            obj.optDouble("turretAngle", 0),
            obj.optInt("lives", 0),
            obj.optBoolean("alive", true)
        );
    }
}
