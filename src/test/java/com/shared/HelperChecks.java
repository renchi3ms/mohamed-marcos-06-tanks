package com.shared;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/** Comprovacions bàsiques executables sense dependències de tests ni obrir la finestra. */
public class HelperChecks {

    public static void main(String[] args) {
        Rectangle2D wall = new Rectangle2D(100, 100, 50, 50);

        // Cercle-cercle
        check(HelperCollisions.circlesOverlap(new Point2D(0, 0), 5, new Point2D(8, 0), 5), "cercles que se solapen");
        check(!HelperCollisions.circlesOverlap(new Point2D(0, 0), 5, new Point2D(20, 0), 5), "cercles separats");

        // Cercle-rectangle: costat, centre i lluny
        check(HelperCollisions.circleOverlapsRectangle(new Point2D(97, 120), 5, wall), "toca el costat esquerre");
        check(HelperCollisions.circleOverlapsRectangle(new Point2D(125, 125), 5, wall), "centre dins del rectangle");
        check(!HelperCollisions.circleOverlapsRectangle(new Point2D(96, 96), 5, wall), "a prop de la cantonada sense tocar-la");
        check(!HelperCollisions.circleOverlapsRectangle(new Point2D(50, 50), 5, wall), "lluny del rectangle");

        // clamp
        close(HelperCollisions.clamp(12, 0, 10), 10, "clamp per sobre del màxim");
        close(HelperCollisions.clamp(-3, 0, 10), 0, "clamp per sota del mínim");

        // Angles: moure's cap a la dreta és 0°, cap a baix 90°
        close(HelperMath.angleForMovement(new Point2D(1, 0)), 0, "angle de moure's a la dreta");
        close(HelperMath.angleForMovement(new Point2D(0, 1)), 90, "angle de moure's cap a baix");

        // Apuntar: des de (0,0) cap a (10,10) l'angle és 45°
        close(HelperMath.angleToTarget(new Point2D(0, 0), new Point2D(10, 10)), 45, "apuntar en diagonal");

        // Direcció: a 45° el vector unitari té components (cos 45, sin 45)
        Point2D dir = HelperMath.directionForAngle(45);
        close(dir.getX(), Math.sqrt(2) / 2, "component X de la direcció a 45°");
        close(dir.getY(), Math.sqrt(2) / 2, "component Y de la direcció a 45°");

        // Moviment: des de (10,10) cap a la dreta, en mig segon a 100 px/s avança 50 px
        Point2D next = HelperMath.positionAtDistance(new Point2D(10, 10), 0, 50);
        close(next.getX(), 60, "posició X després de moure");
        close(next.getY(), 10, "posició Y després de moure");

        System.out.println("HelperChecks: totes les comprovacions són correctes.");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static void close(double actual, double expected, String description) {
        check(Math.abs(actual - expected) < 0.000001, description + ": " + actual + " != " + expected);
    }
}
