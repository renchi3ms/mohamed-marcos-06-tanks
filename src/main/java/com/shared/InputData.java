package com.shared;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Intencions d'un client (C -> S). El servidor nomes en registra l'ultim
 * estat, no el interpreta com a moviment absolut.
 */
public class InputData {

    public List<String> keys = new ArrayList<>();
    public double aimX;
    public double aimY;
    public boolean shoot;
    // Cert si ja s'ha disparat amb aquest input (focus per clic, no mantingut).
    public boolean shootFired = false;

    public InputData() { }

    public InputData(List<String> keys, double aimX, double aimY, boolean shoot) {
        this.keys = keys;
        this.aimX = aimX;
        this.aimY = aimY;
        this.shoot = shoot;
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }

    public JSONObject toJSON() {
        JSONArray arr = new JSONArray();
        for (String k : keys) arr.put(k);
        JSONObject obj = new JSONObject();
        obj.put("keys", arr);
        obj.put("aimX", aimX);
        obj.put("aimY", aimY);
        obj.put("shoot", shoot);
        return obj;
    }

    public static InputData fromJSON(JSONObject obj) {
        InputData in = new InputData();
        JSONArray arr = obj.optJSONArray("keys");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                in.keys.add(arr.getString(i));
            }
        }
        in.aimX = obj.optDouble("aimX", 0);
        in.aimY = obj.optDouble("aimY", 0);
        in.shoot = obj.optBoolean("shoot", false);
        return in;
    }
}
