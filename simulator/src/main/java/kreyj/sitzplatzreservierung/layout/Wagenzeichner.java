package kreyj.sitzplatzreservierung.layout;

import kreyj.sitzplatzreservierung.layout.Form.Beschriftung;
import kreyj.sitzplatzreservierung.layout.Form.Piktogramm;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Ausstattung;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Ausstattungsart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Bereich;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Bereichsart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Einbau;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Einbauart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Sitzplatz;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tisch;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tuer;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tuerart;
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
 * Zeichnet ein {@link Wagenlayout} als Draufsicht. Der Client muss die gelieferten {@link Form}en nur der Reihe nach als
 * SVG-Pfade mit den Stilklassen ausgeben; Geometrie, Sitzausrichtung, Türen und Piktogramme berechnet der Zeichner.
 */
public final class Wagenzeichner {

    /*  Zeichenreihenfolge der Schichten, hinten nach vorn  */
    private static final List<String> SCHICHTEN = List.of("kontur", "zone", "flaeche", "sitzbereich", "sitz", "sitzmerkmal", "tisch",
            "tuer", "ausstattung", "zeichenhilfe");
    private static final double LEHNE_TIEFE = .13;
    private static final double ARMLEHNE_BREITE = .06;

    private Wagenzeichner() {
    }

    /**
     * @param plan      Belegung der Sitzplätze, null für ein Layout ohne Belegung
     * @param gewaehlt  Fahrtabschnitt, für den der Zustand der Plätze gezeichnet wird, null ohne Zustand
     */
    public static Wagenansicht zeichne(Wagenlayout layout, Belegungsplan plan, Fahrtabschnitt gewaehlt, Sitzfaerbung faerbung) {
        var schichten = new LinkedHashMap<String, List<Form>>();
        SCHICHTEN.forEach((schicht) -> schichten.put(schicht, new ArrayList<>()));

        schichten.get("kontur").add(new Form("kontur", null, pfad(layout.kontur()), stile("kontur", "kontur"), null, null, Map.of()));
        for (var element : layout.elemente())
            switch (element) {
                case Sitzplatz sitz -> zeichneSitz(layout, plan, gewaehlt, faerbung, sitz, schichten);
                case Tisch tisch -> schichten.get("tisch").add(form(tisch.id(), tisch.ebene(), tisch.nurPersonal(), pfad(tisch.umriss()),
                        stile("tisch", "tisch", "tisch-" + name(tisch.art())), null, null, Map.of("art", tisch.art().name())));
                case Einbau einbau -> schichten.get("flaeche").add(form(einbau.id(), einbau.ebene(), einbau.nurPersonal(), pfad(einbau.umriss()),
                        stile("flaeche", "einbau", "einbau-" + name(einbau.art())),
                        einbau.art() == Einbauart.WC ? new Beschriftung("WC", mitteVersetzt(einbau.umriss(), .07), .17) : null, null,
                        Map.of("art", einbau.art().name())));
                case Bereich bereich -> zeichneBereich(bereich, schichten);
                case Zonenflaeche zone -> schichten.get("zone").add(form(zone.id(), zone.ebene(), zone.nurPersonal(), pfad(zone.umriss()),
                        stile("zone", "zone", "zone-" + name(zone.zone())), null, null, Map.of("zone", zone.zone().name())));
                case Tuer tuer -> zeichneTuer(layout, tuer, schichten);
                case Ausstattung ausstattung -> zeichneAusstattung(ausstattung, schichten);
            }

        var formen = SCHICHTEN.stream().flatMap((schicht) -> schichten.get(schicht).stream()).toList();
        return new Wagenansicht(layout.laengeM(), layout.breiteM(), layout.ebenen(), formen);
    }

    private static void zeichneSitz(Wagenlayout layout, Belegungsplan plan, Fahrtabschnitt gewaehlt, Sitzfaerbung faerbung, Sitzplatz sitz,
                                    Map<String, List<Form>> schichten) {
        var stile = new ArrayList<>(stile("sitz", "sitz"));
        var farbe = switch (faerbung) {
            case KLASSE -> sitz.klasse() == Klasse.ERSTE ? "farbe-erste" : null;
            case BEREICH -> sitz.zone() == Zone.STANDARD || sitz.zone() == Zone.SONDERNUTZUNG ? null : "farbe-" + name(sitz.zone());
            case STANDARD -> null;
        };
        if (farbe != null)
            stile.add(farbe);

        var details = new LinkedHashMap<String, String>();
        if (sitz.nummer() > 0)
            details.put("Platz", String.valueOf(sitz.nummer()));
        details.put("Klasse", sitz.klasse().name());
        details.put("Bereich", sitz.zone().name());
        details.put("Bauart", sitz.bauart().name());
        details.put("Merkmale", sitz.merkmale().stream().sorted().map(Enum::name).collect(Collectors.joining(", ")));
        if (plan != null && gewaehlt != null && sitz.nummer() > 0) {
            var zustand = plan.zustand(sitz.nummer(), gewaehlt);
            stile.add("zustand-" + name(zustand));
            details.put("Zustand", zustand.name());
        }
        if (sitz.nurPersonal())
            stile.add("nur-personal");

        var mitte = mitteVersetzt(sitz.umriss(), .09);
        schichten.get("sitz").add(new Form(sitz.id(), sitz.ebene(), pfad(sitz.umriss()), List.copyOf(stile),
                sitz.nummer() > 0 ? new Beschriftung(String.valueOf(sitz.nummer()), mitte, .27) : null, null, details));

        /*  Achsen: f zeigt zur Rückenlehne, v quer dazu; u und v sind Koordinaten der Sitzpunkte in diesen Achsen  */
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
        var achsen = new Achsen(fx, fy, vx, vy);

        /*  Rückenlehne mit Armlehnen als U-Form an der Rückseite  */
        var laenge = (u1 - u0) * .62;
        var lehne = List.of(
                achsen.punkt(u0, v0), achsen.punkt(u0 + laenge, v0), achsen.punkt(u0 + laenge, v0 + ARMLEHNE_BREITE),
                achsen.punkt(u0 + LEHNE_TIEFE, v0 + ARMLEHNE_BREITE), achsen.punkt(u0 + LEHNE_TIEFE, v1 - ARMLEHNE_BREITE),
                achsen.punkt(u0 + laenge, v1 - ARMLEHNE_BREITE), achsen.punkt(u0 + laenge, v1), achsen.punkt(u0, v1));
        var lehnenStile = new ArrayList<>(stile);
        lehnenStile.set(1, "sitz-lehne");
        schichten.get("sitz").add(new Form(sitz.id() + "/lehne", sitz.ebene(), ring(lehne), List.copyOf(lehnenStile), null, null, Map.of()));

        /*  Fenster- und Gangseite nur bei Sitzen quer zur Längsachse: Fenster zur Wand, Gang zur Wagenmitte  */
        var mittelpunkt = sitz.umriss().mitte();
        if (Math.abs(vy) > .7) {
            var seite = ((mittelpunkt.x() - layout.laengeM() / 2) * vx + (mittelpunkt.y() - layout.breiteM() / 2) * vy) >= 0 ? 1 : -1;
            var fensterKante = seite > 0 ? v1 : v0;
            var gangKante = seite > 0 ? v0 : v1;
            if (sitz.merkmale().contains(Sitzmerkmal.FENSTER))
                merkmal(schichten, sitz, "fenster", linie(achsen.punkt(u0 + .04, fensterKante + seite * .045), achsen.punkt(u1 - .04, fensterKante + seite * .045)));
            if (sitz.merkmale().contains(Sitzmerkmal.GANG))
                merkmal(schichten, sitz, "gang", linie(achsen.punkt(u0 + .04, gangKante - seite * .045), achsen.punkt(u1 - .04, gangKante - seite * .045)));
        }

        /*  Steckdose (Rechteck) und Leseleuchte (Kreis) am vorderen Sitzrand  */
        var vorn = u1 - .1;
        var mitteQuer = (v0 + v1) / 2;
        var anzahl = (sitz.merkmale().contains(Sitzmerkmal.STECKDOSE) ? 1 : 0) + (sitz.merkmale().contains(Sitzmerkmal.LESELEUCHTE) ? 1 : 0);
        var quer = anzahl == 2 ? mitteQuer - .08 : mitteQuer;
        if (sitz.merkmale().contains(Sitzmerkmal.STECKDOSE)) {
            var p = achsen.punkt(vorn, quer);
            merkmal(schichten, sitz, "steckdose", ring(List.of(new Punkt(p.x() - .05, p.y() - .035), new Punkt(p.x() + .05, p.y() - .035),
                    new Punkt(p.x() + .05, p.y() + .035), new Punkt(p.x() - .05, p.y() + .035))));
            quer += .16;
        }
        if (sitz.merkmale().contains(Sitzmerkmal.LESELEUCHTE))
            merkmal(schichten, sitz, "leseleuchte", kreis(achsen.punkt(vorn, quer), .05));
    }

    private static void merkmal(Map<String, List<Form>> schichten, Sitzplatz sitz, String art, String pfad) {
        schichten.get("sitzmerkmal").add(form(sitz.id() + "/" + art, sitz.ebene(), sitz.nurPersonal(), pfad, stile("sitzmerkmal", "merkmal-" + art), null, null,
                Map.of()));
    }

    private static void zeichneBereich(Bereich bereich, Map<String, List<Form>> schichten) {
        var schicht = bereich.art() == Bereichsart.SITZBEREICH ? "sitzbereich" : "flaeche";
        var piktogramm = piktogramm(bereich.art()).map((art) -> new Piktogramm(art, bereich.umriss().mitte(),
                Math.max(.25, Math.min(.55, bereich.umriss().kuerzesteSeite() * .8)))).orElse(null);
        schichten.get(schicht).add(form(bereich.id(), bereich.ebene(), bereich.nurPersonal(), pfad(bereich.umriss()),
                stile(schicht, "bereich", "bereich-" + name(bereich.art())), null, piktogramm, Map.of("art", bereich.art().name())));
    }

    /*  Ausstieg: Schiebetür aus zwei versetzten Flügeln; Tür: geschlossener Flügel; Durchgang: Schwelle  */
    private static void zeichneTuer(Wagenlayout layout, Tuer tuer, Map<String, List<Form>> schichten) {
        var dx = tuer.nach().x() - tuer.von().x();
        var dy = tuer.nach().y() - tuer.von().y();
        var laenge = Math.hypot(dx, dy);
        var mx = (tuer.von().x() + tuer.nach().x()) / 2;
        var my = (tuer.von().y() + tuer.nach().y()) / 2;
        var nx = laenge == 0 ? 0 : -dy / laenge;
        var ny = laenge == 0 ? 0 : dx / laenge;

        /*  Versatz der Flügel zur Wagenmitte  */
        if (nx * (layout.laengeM() / 2 - mx) + ny * (layout.breiteM() / 2 - my) < 0) {
            nx = -nx;
            ny = -ny;
        }

        var pfad = tuer.art() == Tuerart.AUSSTIEG
                ? linie(new Punkt(tuer.von().x() + nx * .05, tuer.von().y() + ny * .05), new Punkt(mx + nx * .05, my + ny * .05))
                        + linie(new Punkt(mx - nx * .05, my - ny * .05), new Punkt(tuer.nach().x() - nx * .05, tuer.nach().y() - ny * .05))
                : linie(tuer.von(), tuer.nach());
        schichten.get("tuer").add(form(tuer.id(), tuer.ebene(), tuer.nurPersonal(), pfad, stile("tuer", "tuer", "tuer-" + name(tuer.art())), null, null,
                Map.of("art", tuer.art().name())));
        if (tuer.art() == Tuerart.AUSSTIEG)
            schichten.get("tuer").add(form(tuer.id() + "/schwelle", tuer.ebene(), tuer.nurPersonal(), linie(tuer.von(), tuer.nach()),
                    stile("tuer", "tuer-schwelle"), null, null, Map.of()));
    }

    private static void zeichneAusstattung(Ausstattung ausstattung, Map<String, List<Form>> schichten) {
        var zeichenhilfe = ausstattung.art() == Ausstattungsart.ZEICHENHILFE;
        var piktogramm = piktogramm(ausstattung.art()).map((art) -> new Piktogramm(art, ausstattung.position(), .3)).orElse(null);
        var details = new LinkedHashMap<String, String>();
        details.put("art", ausstattung.art().name());
        if (ausstattung.name() != null)
            details.put("name", ausstattung.name());
        schichten.get(zeichenhilfe ? "zeichenhilfe" : "ausstattung").add(form(ausstattung.id(), ausstattung.ebene(), ausstattung.nurPersonal(),
                kreis(ausstattung.position(), zeichenhilfe ? .04 : piktogramm != null ? .2 : .06),
                stile(zeichenhilfe ? "zeichenhilfe" : "ausstattung", "ausstattung-" + name(ausstattung.art())), null, piktogramm, details));
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

    /*  Koordinaten der Sitzpunkte in den Sitzachsen (f: Richtung der Rückenlehne, v: quer dazu) zurück in Wagenkoordinaten  */
    private record Achsen(double fx, double fy, double vx, double vy) {

        Punkt punkt(double u, double v) {
            return new Punkt(u * fx + v * vx, u * fy + v * vy);
        }
    }

    private static Form form(String id, Ebene ebene, boolean nurPersonal, String pfad, List<String> stile, Beschriftung beschriftung,
                             Piktogramm piktogramm, Map<String, String> details) {
        var alle = new ArrayList<>(stile);
        if (nurPersonal)
            alle.add("nur-personal");
        return new Form(id, ebene, pfad, List.copyOf(alle), beschriftung, piktogramm, details);
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

    private static Punkt mitteVersetzt(Umriss umriss, double dy) {
        var mitte = umriss.mitte();
        return new Punkt(mitte.x(), mitte.y() + dy);
    }

    private static String pfad(Umriss umriss) {
        return umriss.ringe().stream().map(Wagenzeichner::ring).collect(Collectors.joining());
    }

    private static String ring(List<Punkt> punkte) {
        var pfad = new StringBuilder();
        for (int i = 0; i < punkte.size(); i++)
            pfad.append(i == 0 ? "M" : "L").append(zahl(punkte.get(i).x())).append(' ').append(zahl(punkte.get(i).y()));
        return pfad.append('Z').toString();
    }

    private static String linie(Punkt von, Punkt nach) {
        return "M" + zahl(von.x()) + " " + zahl(von.y()) + "L" + zahl(nach.x()) + " " + zahl(nach.y());
    }

    private static String kreis(Punkt mitte, double radius) {
        return "M" + zahl(mitte.x() - radius) + " " + zahl(mitte.y()) + "a" + zahl(radius) + " " + zahl(radius) + " 0 1 0 " + zahl(2 * radius) + " 0a"
                + zahl(radius) + " " + zahl(radius) + " 0 1 0 " + zahl(-2 * radius) + " 0Z";
    }

    /*  Auf drei Nachkommastellen, ohne überflüssige Nullen  */
    private static String zahl(double wert) {
        return BigDecimal.valueOf(Math.round(wert * 1000) / 1000.0).stripTrailingZeros().toPlainString();
    }
}
