package com.shared;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

// Comprovacions de col·lisió senzilles: només miren si dues formes es toquen ARA.
public final class HelperCollisions {

    private HelperCollisions() { }

    // Dos cercles es toquen si la distància entre els centres
    // és més petita que la suma dels radis.
    public static boolean circlesOverlap(Point2D centerA, double radiusA, Point2D centerB, double radiusB) {
        return centerA.distance(centerB) < radiusA + radiusB;
    }

    // Un cercle toca un rectangle si el punt del rectangle més proper al centre
    // és a menys d'un radi de distància.
    public static boolean circleOverlapsRectangle(Point2D center, double radius, Rectangle2D rectangle) {
        double nearestX = clamp(center.getX(), rectangle.getMinX(), rectangle.getMaxX());
        double nearestY = clamp(center.getY(), rectangle.getMinY(), rectangle.getMaxY());
        return center.distance(nearestX, nearestY) < radius;
    }

    // Limitar un valor a l'interval indicat: clamp(12, 0, 10) retorna 10.
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
