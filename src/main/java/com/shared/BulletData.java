package com.shared;

import org.json.JSONObject;

/**
 * Estat autoritatiu d'una bala (S -> C).
 */
public class BulletData {

    public String id;
    public String ownerId;      // id del TankData que la va disparar
    public double x;
    public double y;
    public double angle;        // en graus
    public int bounces;         // 0 = no ha rebotat, 1 = rebotada, 2 = morta

    public BulletData() { }

    public BulletData(String id, String ownerId, double x, double y, double angle, int bounces) {
        this.id = id;
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.bounces = bounces;
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }

    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("ownerId", ownerId);
        obj.put("x", x);
        obj.put("y", y);
        obj.put("angle", angle);
        obj.put("bounces", bounces);
        return obj;
    }

    public static BulletData fromJSON(JSONObject obj) {
        return new BulletData(
            obj.optString("id", null),
            obj.optString("ownerId", ""),
            obj.optDouble("x", 0),
            obj.optDouble("y", 0),
            obj.optDouble("angle", 0),
            obj.optInt("bounces", 0)
        );
    }
}
