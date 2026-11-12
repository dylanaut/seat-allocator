package kreyj.sitzplatzreservierung.service;

import kreyj.sitzplatzreservierung.layout.Punkt;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Bereich;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Bereichsart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Sitzplatz;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tisch;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tischart;
import kreyj.sitzplatzreservierung.layout.Wagenlayout;
import kreyj.sitzplatzreservierung.model.Platzart;
import kreyj.sitzplatzreservierung.model.Zone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Leitet aus einem {@link Wagenlayout} die reservierbaren Plätze eines Wagens ab: Sitze mit Platznummer sowie je ein Rollstuhl- und
 * Fahrradplatz pro entsprechender Fläche. Reihe, Spalte und Gangseite ergeben sich aus der Lage der Sitze im Wagen.
 */
public final class PlatzAbleitung {

    /*  Sitze, deren Mitten weniger weit auseinander liegen, gehören zur selben Reihe bzw. Spalte  */
    private static final double REIHEN_TOLERANZ = .12;
    private static final double SPALTEN_TOLERANZ = .12;

    /*  Sitze bis zu diesem Abstand vom Tisch gehören zu seiner Tischgruppe; Gruppenkontingente brauchen mindestens vier Sitze  */
    private static final double TISCH_ABSTAND = .6;
    private static final int KONTINGENT_MINDESTGROESSE = 4;

    /*  Reihen verschiedener Ebenen liegen so weit auseinander, dass sie nie benachbart sind  */
    private static final int EBENEN_ABSTAND = 1000;

    private PlatzAbleitung() {
    }

    /**
     * @param elementId   Id des Layoutelements, das den Platz zeichnet
     * @param kontingent  true bei Sitzen der Tischgruppe, die als Gruppenkontingent angeboten wird
     */
    public record Platz(String id, String bezeichnung, String elementId, Platzart art, int reihe, int spalte, int gangseite,
                        Zone zone, boolean kontingent) {
    }

    public static List<Platz> ableiten(String wagenId, Wagenlayout layout) {
        var plaetze = new ArrayList<Platz>();
        var sitze = layout.sitzplaetze().stream().filter((s) -> s.nummer() > 0 && !s.nurPersonal()).toList();
        var kontingent = kontingentSitze(layout, sitze);

        for (var ebene : layout.ebenen()) {
            var aufEbene = sitze.stream().filter((s) -> s.ebene() == ebene).toList();
            if (aufEbene.isEmpty())
                continue;
            var versatz = layout.ebenen().indexOf(ebene) * EBENEN_ABSTAND;
            var reihen = gruppieren(aufEbene.stream().map((s) -> s.umriss().mitte().x()).toList(), REIHEN_TOLERANZ);
            var gang = gangPosition(gruppieren(aufEbene.stream().map((s) -> s.umriss().mitte().y()).toList(), SPALTEN_TOLERANZ));

            /*  Die Spalte ist die Stelle des Sitzes in seiner Reihe von oben nach unten  */
            var reiheNachSitz = new HashMap<Sitzplatz, Integer>();
            aufEbene.forEach((sitz) -> reiheNachSitz.put(sitz, naechste(reihen, sitz.umriss().mitte().x())));
            for (var sitz : aufEbene) {
                var reihe = reiheNachSitz.get(sitz);
                var spalte = (int) aufEbene.stream().filter((s) -> reiheNachSitz.get(s).equals(reihe) && s.umriss().mitte().y() < sitz.umriss().mitte().y() - SPALTEN_TOLERANZ).count();
                plaetze.add(new Platz(wagenId + "-" + sitz.nummer(), String.valueOf(sitz.nummer()), sitz.id(), Platzart.SITZ, versatz + reihe, spalte,
                        sitz.umriss().mitte().y() > gang ? 1 : 0, zone(sitz.zone()), kontingent.contains(sitz)));
            }
        }

        /*  Sonderplätze liegen in der Reihe und Spalte des nächsten Sitzes derselben Ebene  */
        var nummern = new int[Platzart.values().length];
        for (var bereich : layout.elemente(Bereich.class)) {
            var art = bereich.art() == Bereichsart.ROLLSTUHL ? Platzart.ROLLSTUHL : bereich.art() == Bereichsart.FAHRRAD ? Platzart.FAHRRAD : null;
            if (art == null || bereich.nurPersonal())
                continue;
            var mitte = bereich.umriss().mitte();
            var nachbar = plaetze.stream().filter((p) -> p.art() == Platzart.SITZ).min(Comparator.comparingDouble((p) -> abstand(layout, p, mitte)));
            var nummer = ++nummern[art.ordinal()];
            var kuerzel = art == Platzart.ROLLSTUHL ? "RS" : "FR";
            plaetze.add(new Platz(wagenId + "-" + kuerzel + nummer, (art == Platzart.ROLLSTUHL ? "Rollstuhl " : "Fahrrad ") + nummer, bereich.id(), art,
                    nachbar.map(Platz::reihe).orElse(0), nachbar.map(Platz::spalte).orElse(0), nachbar.map(Platz::gangseite).orElse(0),
                    Zone.STANDARD, false));
        }
        return plaetze;
    }

    /*  Sitze der Tischgruppe mit den meisten Sitzen, falls sie mindestens vier Sitze hat; sonst keine  */
    private static Set<Sitzplatz> kontingentSitze(Wagenlayout layout, List<Sitzplatz> sitze) {
        var tische = layout.elemente(Tisch.class).stream().filter((t) -> t.art() == Tischart.LANG || t.art() == Tischart.KURZ).toList();
        var beste = Set.<Sitzplatz>of();
        for (var tisch : tische) {
            var gruppe = sitze.stream().filter((s) -> s.ebene() == tisch.ebene() && tisch.umriss().abstand(s.umriss().mitte()) <= TISCH_ABSTAND)
                    .collect(Collectors.toSet());
            if (gruppe.size() >= KONTINGENT_MINDESTGROESSE && gruppe.size() > beste.size())
                beste = gruppe;
        }
        return beste;
    }

    private static double abstand(Wagenlayout layout, Platz platz, Punkt punkt) {
        var sitz = layout.sitzplaetze().stream().filter((s) -> s.id().equals(platz.elementId())).findFirst().orElseThrow().umriss().mitte();
        return Math.hypot(sitz.x() - punkt.x(), sitz.y() - punkt.y());
    }

    /*  Zusammenhängende Werte mit Abständen bis zur Toleranz bilden eine Gruppe; Ergebnis sind die Gruppenmitten aufsteigend  */
    private static List<Double> gruppieren(List<Double> werte, double toleranz) {
        var sortiert = werte.stream().sorted().toList();
        var mitten = new ArrayList<Double>();
        var gruppe = new ArrayList<Double>();
        for (var wert : sortiert) {
            if (!gruppe.isEmpty() && wert - gruppe.getLast() > toleranz) {
                mitten.add(gruppe.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
                gruppe.clear();
            }
            gruppe.add(wert);
        }
        if (!gruppe.isEmpty())
            mitten.add(gruppe.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
        return mitten;
    }

    private static int naechste(List<Double> mitten, double wert) {
        var index = 0;
        for (var i = 1; i < mitten.size(); i++)
            if (Math.abs(mitten.get(i) - wert) < Math.abs(mitten.get(index) - wert))
                index = i;
        return index;
    }

    /*  Querposition des Gangs: Mitte des größten Abstands zwischen zwei Sitzspalten; unendlich bei nur einer Spalte  */
    private static double gangPosition(List<Double> spalten) {
        var position = Double.POSITIVE_INFINITY;
        var groesse = 0.0;
        for (var i = 1; i < spalten.size(); i++)
            if (spalten.get(i) - spalten.get(i - 1) > groesse) {
                groesse = spalten.get(i) - spalten.get(i - 1);
                position = (spalten.get(i) + spalten.get(i - 1)) / 2;
            }
        return position;
    }

    private static Zone zone(kreyj.sitzplatzreservierung.layout.Zone zone) {
        return switch (zone) {
            case RUHE -> Zone.RUHE;
            case FAMILIE, KLEINKIND -> Zone.KINDER;
            default -> Zone.STANDARD;
        };
    }
}
