package kreyj.sitzplatzreservierung.layout;

import kreyj.sitzplatzreservierung.layout.Form.Beschriftung;
import kreyj.sitzplatzreservierung.layout.Form.Piktogramm;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Ausstattung;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Ausstattungsart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Bereich;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Bereichsart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Sitzplatz;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tisch;
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
    private static final double LEHNE_TIEFE = .13;
    private static final double ARMLEHNE_BREITE = .06;

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

        schichten.get("kontur").add(new Form("kontur", null, pfad(layout.kontur()), stile("kontur", "kontur"), null, null, Map.of()));
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
        return new Wagenansicht(layout.laengeM(), layout.breiteM(), layout.ebenen(), formen);
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
        schichten.get("platz").add(new Form(bezug != null ? bezug.platzId() : sitz.id(), sitz.ebene(), pfad(sitz.umriss()), List.copyOf(stile),
                bezug != null ? new Beschriftung(bezug.bezeichnung(), new Punkt(mitte.x(), mitte.y() + .08), bezug.bezeichnung().length() > 2 ? .21 : .27) : null, null, details));

        /*  Rückenlehne mit Armlehnen als U-Form an der Rückseite; f zeigt zur Lehne, v quer dazu  */
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
        var laenge = (u1 - u0) * .62;
        var lehne = List.of(
                punkt(fx, fy, vx, vy, u0, v0), punkt(fx, fy, vx, vy, u0 + laenge, v0), punkt(fx, fy, vx, vy, u0 + laenge, v0 + ARMLEHNE_BREITE),
                punkt(fx, fy, vx, vy, u0 + LEHNE_TIEFE, v0 + ARMLEHNE_BREITE), punkt(fx, fy, vx, vy, u0 + LEHNE_TIEFE, v1 - ARMLEHNE_BREITE),
                punkt(fx, fy, vx, vy, u0 + laenge, v1 - ARMLEHNE_BREITE), punkt(fx, fy, vx, vy, u0 + laenge, v1), punkt(fx, fy, vx, vy, u0, v1));
        schichten.get("platz").add(new Form((bezug != null ? bezug.platzId() : sitz.id()) + "/lehne", sitz.ebene(), ring(lehne),
                stile("platz", "platz-lehne", "bereich-" + name(sitz.zone())), null, null, Map.of()));
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

    /*  Koordinaten in den Sitzachsen (f: Richtung der Rückenlehne, v: quer dazu) zurück in Wagenkoordinaten  */
    private static Punkt punkt(double fx, double fy, double vx, double vy, double u, double v) {
        return new Punkt(u * fx + v * vx, u * fy + v * vy);
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
