package com.shared;

import javafx.geometry.Point2D;

// Angles i moviment. Tots els angles es reben i es retornen en graus.
public final class HelperMath {

    private HelperMath() { }

    // Angle segons la direcció (horizontal, vertical).
    public static double angleForMovement(Point2D direction) {
        return Math.toDegrees(Math.atan2(direction.getY(), direction.getX()));
    }

    // Angle per apuntar des de position cap a target.
    public static double angleToTarget(Point2D position, Point2D target) {
        return angleForMovement(target.subtract(position));
    }

    // Desplaçament d'un píxel en la direcció indicada: dreta = (1, 0), avall = (0, 1).
    public static Point2D directionForAngle(double angle) {
        double radians = Math.toRadians(angle);
        return new Point2D(Math.cos(radians), Math.sin(radians));
    }

    // Punt situat a la distància indicada, seguint aquest angle des de la posició inicial.
    public static Point2D positionAtDistance(Point2D position, double angle, double distance) {
        return position.add(directionForAngle(angle).multiply(distance));
    }
}
