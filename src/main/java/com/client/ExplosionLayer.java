package com.client;

import java.util.ArrayList;
import java.util.List;

import com.shared.Explosion;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Capa d'efectes visuals (explosions) que es col-loca a la pila de vistes
 * (StackPane) per sobre de totes les vistes, de manera que les explosions
 * es continuen veient quan la partida acaba i la vista de joc es substitueix
 * per la de resultat.
 *
 * Les explosions es dibuixen amb Circles que mouem i escalem des de la
 * capa: com que la capa te la mateixa mida que la pila de vistes, les
 * coordenades del Canvas de joc es converteixen a les de la capa amb la
 * cadena local -> escena -> capa, i aixi queden ben alineades fins i tot
 * si la finestra es redimensiona.
 */
public class ExplosionLayer extends Pane {

    private static final long BULLET_LIFE_NS = 350_000_000L; // vida d'una explosio de bala
    private static final long TANK_LIFE_NS = 600_000_000L;    // vida d'una explosio de tanc

    private static final int MAX_CIRCLES = 64; // nomes calen mentre hi ha explosions actives

    private final List<Explosion> explosions = new ArrayList<>();
    private final Circle[] circles = new Circle[MAX_CIRCLES];

    public ExplosionLayer() {
        setMouseTransparent(true);

        for (int i = 0; i < MAX_CIRCLES; i++) {
            Circle c = new Circle();
            c.setVisible(false);
            getChildren().add(c);
            circles[i] = c;
        }

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                paintFrame(now);
            }
        };
        timer.start();
    }

    /** Afegeix una explosio nova a la capa (cridat des del fil JavaFX). */
    public void spawn(double x, double y, double maxRadius) {
        explosions.add(new Explosion(x, y, maxRadius));
    }

    public void clear() {
        explosions.clear();
    }

    /** Esborra les explosions caduques i recoloca els cercles actius. */
    private void paintFrame(long now) {
        Canvas playCanvas = Main.playCanvas;
        if (playCanvas == null) return;

        int i = 0;
        var it = explosions.iterator();
        while (it.hasNext() && i < MAX_CIRCLES) {
            Explosion ex = it.next();
            long life = (ex.maxRadius < 20) ? BULLET_LIFE_NS : TANK_LIFE_NS;
            if (ex.isOver(now, life)) {
                it.remove();
                continue;
            }

            double p = ex.progress(now, life);
            double r = ex.maxRadius * (0.3 + 0.7 * p);

            Point2D scene = playCanvas.localToScene(ex.x, ex.y);
            Point2D here = this.sceneToLocal(scene);

            Circle c = circles[i++];
            c.setCenterX(here.getX());
            c.setCenterY(here.getY());
            c.setRadius(r);
            c.setFill(Color.color(1.0, 0.65 - 0.45 * p, 0.0, 1.0 - p));
            c.setVisible(true);
        }
        while (i < MAX_CIRCLES) {
            circles[i++].setVisible(false);
        }
    }
}
