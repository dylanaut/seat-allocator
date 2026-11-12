package kreyj.sitzplatzreservierung.layout;

import kreyj.sitzplatzreservierung.layout.Form.Beschriftung;
import kreyj.sitzplatzreservierung.layout.Form.Piktogramm;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Ausstattung;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Ausstattungsart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Bereich;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Bereichsart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Sitzplatz;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tisch;
import kreyj.sitzplatzreservierung.layout.Wagenansicht.Ausschnitt;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Zonenflaeche;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Zeichnet ein {@link Wagenlayout} als Draufsicht für die Platzwahl, reduziert auf Wagenumriss, Zonen, Tische, Plätze und Piktogramme.
 * Der Client muss die gelieferten {@link Form}en nur der Reihe nach als SVG-Pfade mit den Stilklassen ausgeben. Die Formen der Plätze
 * tragen die Id des Platzes (Stilklasse "platz"), damit der Client Zustand und Auswahl darüber zuordnen kann.
 */
public final class Sitzplanzeichner {

    /*  Zeichenreihenfolge der Schichten, hinten nach vorn  */
    private static final List<String> SCHICHTEN = List.of("kontur", "zone", "tisch", "platz", "icon");

    /*  Leere Wagenenden werden weggelassen, sobald mehr als MINDEST_KUERZUNG Meter wegfallen; um den Inhalt bleibt RAND Meter frei  */
    private static final double RAND = .6;
    private static final double MINDEST_KUERZUNG = 1.0;

    /*  Länge der gepunkteten Brücke, die den Umriss am Schnitt andeutet (zwei Punkte)  */
    private static final double BRUECKE = .2;

    /*  Tiefe der konkaven Sitzkante als Anteil der Sitztiefe  */
    private static final double KANTE_TIEFE = .18;

    private Sitzplanzeichner() {
    }

    /**
     * Der reservierbare Platz, den ein Layoutelement darstellt.
     *
     * @param art  Art des Platzes: "sitz", "rollstuhl" oder "fahrrad"
     */
    public record PlatzBezug(String platzId, String bezeichnung, String art) {
    }

    /**
     * @param plaetze  die reservierbaren Plätze nach Id des Layoutelements; Sitze und Flächen ohne Eintrag sind nicht reservierbar
     */
    public static Wagenansicht zeichne(Wagenlayout layout, Map<String, PlatzBezug> plaetze) {
        var schichten = new LinkedHashMap<String, List<Form>>();
        SCHICHTEN.forEach((schicht) -> schichten.put(schicht, new ArrayList<>()));

        var ausschnitte = layout.ebenen().stream().map((ebene) -> ausschnitt(layout, ebene, plaetze)).toList();
        for (var ausschnitt : ausschnitte)
            zeichneKontur(layout, ausschnitt, schichten.get("kontur"));
        for (var element : layout.elemente()) {
            if (element.nurPersonal())
                continue;
            switch (element) {
                case Sitzplatz sitz -> zeichneSitz(sitz, plaetze.get(sitz.id()), schichten);
                case Tisch tisch -> schichten.get("tisch").add(new Form(tisch.id(), tisch.ebene(), pfad(tisch.umriss()),
                        stile("tisch", "tisch", "tisch-" + name(tisch.art())), null, null, Map.of()));
                case Zonenflaeche zone -> schichten.get("zone").add(new Form(zone.id(), zone.ebene(), pfad(zone.umriss()),
                        stile("zone", "zone", "zone-" + name(zone.zone())), null, null, Map.of("Bereich", zone.zone().name())));
                case Bereich bereich -> zeichneBereich(bereich, plaetze.get(bereich.id()), schichten);
                case Ausstattung ausstattung -> piktogramm(ausstattung.art()).ifPresent((art) -> schichten.get("icon").add(
                        new Form(ausstattung.id(), ausstattung.ebene(), "", stile("icon", "icon", "icon-" + name(ausstattung.art())), null,
                                new Piktogramm(art, ausstattung.position(), .3), Map.of())));
                default -> {
                    /*  Einbauten, Türen und Wände gehören nicht zur Platzwahl  */
                }
            }
        }

        var formen = SCHICHTEN.stream().flatMap((schicht) -> schichten.get(schicht).stream()).toList();
        return new Wagenansicht(layout.laengeM(), layout.breiteM(), layout.ebenen(), ausschnitte, formen);
    }

    /*  Längsbereich einer Ebene, in dem Plätze, Tische oder Piktogramme liegen, mit Rand; leere Enden fallen weg  */
    private static Ausschnitt ausschnitt(Wagenlayout layout, Ebene ebene, Map<String, PlatzBezug> plaetze) {
        var von = Double.MAX_VALUE;
        var bis = -Double.MAX_VALUE;
        for (var element : layout.elemente()) {
            if (element.ebene() != ebene || element.nurPersonal())
                continue;
            switch (element) {
                case Sitzplatz sitz -> {
                    von = Math.min(von, sitz.umriss().minX());
                    bis = Math.max(bis, sitz.umriss().maxX());
                }
                case Tisch tisch -> {
                    von = Math.min(von, tisch.umriss().minX());
                    bis = Math.max(bis, tisch.umriss().maxX());
                }
                case Bereich bereich when piktogramm(bereich.art()).isPresent() -> {
                    von = Math.min(von, bereich.umriss().minX());
                    bis = Math.max(bis, bereich.umriss().maxX());
                }
                case Ausstattung ausstattung when ausstattung.art() != Ausstattungsart.KUPPLUNG && piktogramm(ausstattung.art()).isPresent() -> {
                    von = Math.min(von, ausstattung.position().x());
                    bis = Math.max(bis, ausstattung.position().x());
                }
                default -> {
                }
            }
        }
        if (von > bis)
            return new Ausschnitt(ebene, 0, layout.laengeM());
        var links = von - RAND > MINDEST_KUERZUNG ? von - RAND : 0;
        var rechts = layout.laengeM() - (bis + RAND) > MINDEST_KUERZUNG ? bis + RAND : layout.laengeM();
        return new Ausschnitt(ebene, links, rechts);
    }

    /*  Fläche des Wagens im Ausschnitt, der Umriss als offene Linien und an jedem Schnitt eine kurze gepunktete Brücke je Umrisslinie  */
    private static void zeichneKontur(Wagenlayout layout, Ausschnitt ausschnitt, List<Form> kontur) {
        var links = ausschnitt.vonXM();
        var rechts = ausschnitt.bisXM();
        var flaeche = new StringBuilder();
        var linien = new StringBuilder();
        var bruecken = new StringBuilder();
        for (var ring : layout.kontur().ringe()) {
            var beschnitten = beschneide(beschneide(ring, links, true), rechts, false);
            if (beschnitten.size() > 2)
                flaeche.append(ring(beschnitten));

            var lauf = new ArrayList<Punkt>();
            for (int i = 0; i < ring.size(); i++) {
                var strecke = kappe(ring.get(i), ring.get((i + 1) % ring.size()), links, rechts);
                if (strecke == null) {
                    linien.append(linie(lauf));
                    lauf.clear();
                    continue;
                }
                if (!lauf.isEmpty() && !gleich(lauf.getLast(), strecke[0])) {
                    linien.append(linie(lauf));
                    lauf.clear();
                }
                if (lauf.isEmpty())
                    lauf.add(strecke[0]);
                lauf.add(strecke[1]);
                for (var p : strecke) {
                    if (links > 0 && Math.abs(p.x() - links) < 1e-6)
                        bruecken.append("M").append(zahl(p.x())).append(' ').append(zahl(p.y())).append("L").append(zahl(p.x() - BRUECKE)).append(' ').append(zahl(p.y()));
                    if (rechts < layout.laengeM() && Math.abs(p.x() - rechts) < 1e-6)
                        bruecken.append("M").append(zahl(p.x())).append(' ').append(zahl(p.y())).append("L").append(zahl(p.x() + BRUECKE)).append(' ').append(zahl(p.y()));
                }
            }
            linien.append(linie(lauf));
        }
        kontur.add(new Form("kontur/" + ausschnitt.ebene(), ausschnitt.ebene(), flaeche.toString(), stile("kontur", "kontur-flaeche"), null, null, Map.of()));
        kontur.add(new Form("kontur-linie/" + ausschnitt.ebene(), ausschnitt.ebene(), linien.toString(), stile("kontur", "kontur-linie"), null, null, Map.of()));
        if (bruecken.length() > 0)
            kontur.add(new Form("kontur-bruecke/" + ausschnitt.ebene(), ausschnitt.ebene(), bruecken.toString(), stile("kontur", "kontur-bruecke"), null, null, Map.of()));
    }

    /*  Sutherland-Hodgman gegen die Gerade x = grenze; behalten wird x >= grenze (links) bzw. x <= grenze (rechts)  */
    private static List<Punkt> beschneide(List<Punkt> ring, double grenze, boolean links) {
        var ergebnis = new ArrayList<Punkt>();
        for (int i = 0; i < ring.size(); i++) {
            var p = ring.get(i);
            var q = ring.get((i + 1) % ring.size());
            var pDrin = links ? p.x() >= grenze : p.x() <= grenze;
            var qDrin = links ? q.x() >= grenze : q.x() <= grenze;
            if (pDrin)
                ergebnis.add(p);
            if (pDrin != qDrin) {
                var t = (grenze - p.x()) / (q.x() - p.x());
                ergebnis.add(new Punkt(grenze, p.y() + t * (q.y() - p.y())));
            }
        }
        return ergebnis;
    }

    /*  Der Teil der Strecke zwischen den Geraden x = links und x = rechts, null wenn nichts übrig bleibt  */
    private static Punkt[] kappe(Punkt p, Punkt q, double links, double rechts) {
        var dx = q.x() - p.x();
        double t0 = 0, t1 = 1;
        if (dx == 0) {
            if (p.x() < links || p.x() > rechts)
                return null;
        } else {
            var ta = (links - p.x()) / dx;
            var tb = (rechts - p.x()) / dx;
            t0 = Math.max(0, Math.min(ta, tb));
            t1 = Math.min(1, Math.max(ta, tb));
            if (t0 > t1)
                return null;
        }
        return new Punkt[] { new Punkt(p.x() + t0 * dx, p.y() + t0 * (q.y() - p.y())), new Punkt(p.x() + t1 * dx, p.y() + t1 * (q.y() - p.y())) };
    }

    private static boolean gleich(Punkt a, Punkt b) {
        return Math.abs(a.x() - b.x()) < 1e-6 && Math.abs(a.y() - b.y()) < 1e-6;
    }

    private static String linie(List<Punkt> punkte) {
        if (punkte.size() < 2)
            return "";
        var pfad = new StringBuilder();
        for (int i = 0; i < punkte.size(); i++)
            pfad.append(i == 0 ? "M" : "L").append(zahl(punkte.get(i).x())).append(' ').append(zahl(punkte.get(i).y()));
        return pfad.toString();
    }

    private static void zeichneSitz(Sitzplatz sitz, PlatzBezug bezug, Map<String, List<Form>> schichten) {
        var stile = new ArrayList<>(stile("platz", "platz", bezug != null ? "platz-sitz" : "platz-gesperrt"));
        if (sitz.klasse() == Klasse.ERSTE)
            stile.add("klasse-erste");
        stile.add("bereich-" + name(sitz.zone()));

        var details = new LinkedHashMap<String, String>();
        details.put("Platz", bezug != null ? bezug.bezeichnung() : sitz.nummer() > 0 ? String.valueOf(sitz.nummer()) : "Personal");
        details.put("Klasse", sitz.klasse().name());
        details.put("Bereich", sitz.zone().name());
        if (!sitz.merkmale().isEmpty())
            details.put("Merkmale", sitz.merkmale().stream().sorted().map(Enum::name).collect(Collectors.joining(", ")));

        var mitte = sitz.umriss().mitte();
        schichten.get("platz").add(new Form(bezug != null ? bezug.platzId() : sitz.id(), sitz.ebene(), sitzpfad(sitz), List.copyOf(stile),
                bezug != null ? new Beschriftung(bezug.bezeichnung(), new Punkt(mitte.x(), mitte.y() + .08), bezug.bezeichnung().length() > 2 ? .21 : .27) : null, null, details));
    }

    /*  Sitzfläche im Rahmen des Sitzes: gerade Kante an der Rückenlehne, die Sitzkante gegenüber als konkave Kurve  */
    private static String sitzpfad(Sitzplatz sitz) {
        /*  f zeigt zur Rückenlehne, v quer dazu; u und v sind die Koordinaten der Sitzpunkte in diesen Achsen  */
        var winkel = Math.toRadians(sitz.lehnenrichtungGrad());
        var fx = Math.sin(winkel);
        var fy = Math.cos(winkel);
        var vx = -fy;
        var vy = fx;
        double u0 = Double.MAX_VALUE, u1 = -Double.MAX_VALUE, v0 = Double.MAX_VALUE, v1 = -Double.MAX_VALUE;
        for (var ring : sitz.umriss().ringe())
            for (var p : ring) {
                var u = p.x() * fx + p.y() * fy;
                var v = p.x() * vx + p.y() * vy;
                u0 = Math.min(u0, u);
                u1 = Math.max(u1, u);
                v0 = Math.min(v0, v);
                v1 = Math.max(v1, v);
            }

        /*  Der Steuerpunkt liegt doppelt so weit innen wie der tiefste Punkt der Kurve  */
        var kante = punkt(fx, fy, vx, vy, u1, v0);
        var ende = punkt(fx, fy, vx, vy, u1, v1);
        var steuer = punkt(fx, fy, vx, vy, u1 - 2 * (u1 - u0) * KANTE_TIEFE, (v0 + v1) / 2);
        var ruecken0 = punkt(fx, fy, vx, vy, u0, v0);
        var ruecken1 = punkt(fx, fy, vx, vy, u0, v1);
        return "M" + zahl(ruecken0.x()) + " " + zahl(ruecken0.y()) + "L" + zahl(kante.x()) + " " + zahl(kante.y())
                + "Q" + zahl(steuer.x()) + " " + zahl(steuer.y()) + " " + zahl(ende.x()) + " " + zahl(ende.y())
                + "L" + zahl(ruecken1.x()) + " " + zahl(ruecken1.y()) + "Z";
    }

    /*  Koordinaten in den Sitzachsen (f: Richtung der Rückenlehne, v: quer dazu) zurück in Wagenkoordinaten  */
    private static Punkt punkt(double fx, double fy, double vx, double vy, double u, double v) {
        return new Punkt(u * fx + v * vx, u * fy + v * vy);
    }

    /*  Rollstuhl- und Fahrradflächen sind Plätze, wenn sie reservierbar sind; Gepäck, Kinderwagen und Klappsitze nur Piktogramme  */
    private static void zeichneBereich(Bereich bereich, PlatzBezug bezug, Map<String, List<Form>> schichten) {
        var art = piktogramm(bereich.art());
        if (art.isEmpty())
            return;
        var piktogramm = new Piktogramm(art.get(), bereich.umriss().mitte(), Math.max(.25, Math.min(.55, bereich.umriss().kuerzesteSeite() * .8)));
        if (bezug != null) {
            var details = new LinkedHashMap<String, String>();
            details.put("Platz", bezug.bezeichnung());
            schichten.get("platz").add(new Form(bezug.platzId(), bereich.ebene(), pfad(bereich.umriss()),
                    stile("platz", "platz", "platz-" + bezug.art(), "bereich-" + name(bereich.art())), null, piktogramm, details));
        } else if (bereich.art() != Bereichsart.ROLLSTUHL && bereich.art() != Bereichsart.FAHRRAD)
            schichten.get("icon").add(new Form(bereich.id(), bereich.ebene(), "", stile("icon", "icon", "icon-" + name(bereich.art())), null, piktogramm, Map.of()));
    }

    private static Optional<Piktogrammart> piktogramm(Bereichsart art) {
        return Optional.ofNullable(switch (art) {
            case ROLLSTUHL -> Piktogrammart.ROLLSTUHL;
            case FAHRRAD -> Piktogrammart.FAHRRAD;
            case KINDERWAGEN -> Piktogrammart.KINDERWAGEN;
            case GEPAECK -> Piktogrammart.GEPAECK;
            case KLAPPSITZ -> Piktogrammart.KLAPPSITZ;
            default -> null;
        });
    }

    private static Optional<Piktogrammart> piktogramm(Ausstattungsart art) {
        return Optional.ofNullable(switch (art) {
            case MONITOR -> Piktogrammart.MONITOR;
            case KUPPLUNG -> Piktogrammart.KUPPLUNG;
            case MUELLEIMER -> Piktogrammart.MUELLEIMER;
            case FAHRERSTAND -> Piktogrammart.FAHRERSTAND;
            case KINDERWAGENPLATZ -> Piktogrammart.KINDERWAGEN;
            case PERSONAL -> Piktogrammart.PERSONAL;
            case WICKELTISCH -> Piktogrammart.WICKELTISCH;
            case SCHLIESSFACH -> Piktogrammart.SCHLIESSFACH;
            case GARDEROBE -> Piktogrammart.GARDEROBE;
            default -> null;
        });
    }

    /*  Die erste Klasse ist die Schicht (schicht-xxx), die übrigen sind Stilklassen  */
    private static List<String> stile(String schicht, String... weitere) {
        var stile = new ArrayList<String>();
        stile.add("schicht-" + schicht);
        stile.addAll(List.of(weitere));
        return stile;
    }

    private static String name(Enum<?> wert) {
        return wert.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String pfad(Umriss umriss) {
        return umriss.ringe().stream().map(Sitzplanzeichner::ring).collect(Collectors.joining());
    }

    private static String ring(List<Punkt> punkte) {
        var pfad = new StringBuilder();
        for (int i = 0; i < punkte.size(); i++)
            pfad.append(i == 0 ? "M" : "L").append(zahl(punkte.get(i).x())).append(' ').append(zahl(punkte.get(i).y()));
        return pfad.append('Z').toString();
    }

    /*  Auf drei Nachkommastellen, ohne überflüssige Nullen  */
    private static String zahl(double wert) {
        return BigDecimal.valueOf(Math.round(wert * 1000) / 1000.0).stripTrailingZeros().toPlainString();
    }
}
