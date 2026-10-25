package com.shared;

/**
 * Efecte visual d'explosio (nomes de client: el servidor mai no l'envia).
 * El client la genera quan una bala o un tanc es destrueix.
 */
public class Explosion {

    public double x;
    public double y;
    public double maxRadius;
    public long born; // System.nanoTime()

    public Explosion(double x, double y, double maxRadius) {
        this.x = x;
        this.y = y;
        this.maxRadius = maxRadius;
        this.born = System.nanoTime();
    }

    public boolean isOver(long now, long lifeNs) {
        return (now - born) >= lifeNs;
    }

    /** Progres de 0 a 1 en el temps 'now' per a una vida de 'lifeNs'. */
    public double progress(long now, long lifeNs) {
        double p = (double) (now - born) / lifeNs;
        return (p < 0.0) ? 0.0 : (p > 1.0) ? 1.0 : p;
    }
}
