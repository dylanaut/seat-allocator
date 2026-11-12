package kreyj.sitzplatzreservierung.layout;

import java.util.List;

/**
 * Fläche aus einem oder mehreren Ringen (Außenkonturen und Löcher); gefüllt wird mit der Even-Odd-Regel.
 */
public record Umriss(List<List<Punkt>> ringe) {

    public Umriss {
        ringe = ringe.stream().map(List::copyOf).toList();
    }

    public Punkt mitte() {
        var g = grenzen();
        return new Punkt((g[0] + g[2]) / 2, (g[1] + g[3]) / 2);
    }

    /**
     * Kleinere Seitenlänge des umschließenden Rechtecks.
     */
    public double kuerzesteSeite() {
        var g = grenzen();
        return Math.min(g[2] - g[0], g[3] - g[1]);
    }

    /**
     * Abstand des Punktes zum umschließenden Rechteck, 0 innerhalb.
     */
    public double abstand(Punkt punkt) {
        var g = grenzen();
        return Math.hypot(Math.max(Math.max(g[0] - punkt.x(), punkt.x() - g[2]), 0), Math.max(Math.max(g[1] - punkt.y(), punkt.y() - g[3]), 0));
    }

    /*  Umschließendes Rechteck als { xMin, yMin, xMax, yMax }  */
    private double[] grenzen() {
        var g = new double[] { Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
        for (var ring : ringe)
            for (var punkt : ring) {
                g[0] = Math.min(g[0], punkt.x());
                g[1] = Math.min(g[1], punkt.y());
                g[2] = Math.max(g[2], punkt.x());
                g[3] = Math.max(g[3], punkt.y());
            }
        return g;
    }
}
