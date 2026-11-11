package kreyj.sitzplatzreservierung.layout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Ausstattung;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Ausstattungsart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Bereich;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Bereichsart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Einbau;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Einbauart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Sitzplatz;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tisch;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tischart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tuer;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tuerart;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Zonenflaeche;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Überführt eine RISMAPS-FeatureCollection (GeoJSON, Grad EPSG:4326) in ein {@link Wagenlayout} in Metern.
 * Nullpunkt ist die obere linke Ecke der bbox, die y-Achse wird gespiegelt.
 */
public final class RismapsImport {

    private static final double METER_JE_GRAD = 111319.49;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RismapsImport() {
    }

    public static Wagenlayout importiere(String kennung, InputStream json) {
        try {
            return importiere(kennung, MAPPER.readTree(json));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Wagenlayout importiere(String kennung, JsonNode wurzel) {
        var bbox = wurzel.path("bbox");
        if (!"FeatureCollection".equals(wurzel.path("type").asText()) || bbox.size() < 4 || !wurzel.path("features").isArray())
            throw new IllegalArgumentException("Keine RISMAPS-FeatureCollection mit bbox: " + kennung);

        var nullpunktX = bbox.get(0).asDouble();
        var nullpunktY = bbox.get(3).asDouble();
        var laenge = (bbox.get(2).asDouble() - nullpunktX) * METER_JE_GRAD;
        var breite = (bbox.get(3).asDouble() - bbox.get(1).asDouble()) * METER_JE_GRAD;

        Umriss kontur = null;
        var elemente = new ArrayList<Wagenelement>();
        for (var feature : wurzel.path("features")) {
            var geometrie = feature.path("geometry");
            if (!geometrie.has("coordinates"))
                continue;

            var id = feature.path("id").asText();
            var art = id.contains(".") ? id.substring(0, id.indexOf('.')) : id;
            var p = feature.path("properties");
            var ebene = RismapsSchluessel.von(Ebene.class, text(p, "level"), Ebene.HAUPTEBENE);
            var nurPersonal = p.path("staffOnly").asBoolean(false);

            switch (art) {
                case "VEHICLE" -> kontur = umriss(geometrie, nullpunktX, nullpunktY);
                case "SEAT" -> elemente.add(sitzplatz(id, ebene, nurPersonal, p, umriss(geometrie, nullpunktX, nullpunktY)));
                case "AREA" -> elemente.add(new Bereich(id, ebene, nurPersonal,
                        RismapsSchluessel.von(Bereichsart.class, text(p, "type"), Bereichsart.SONSTIGE), umriss(geometrie, nullpunktX, nullpunktY)));
                case "ZONE" -> elemente.add(new Zonenflaeche(id, ebene, nurPersonal,
                        RismapsSchluessel.von(Zone.class, text(p, "zone"), Zone.STANDARD), umriss(geometrie, nullpunktX, nullpunktY)));
                case "INTERIOR" -> {
                    var interiorType = String.valueOf(text(p, "interiorType"));
                    var umriss = umriss(geometrie, nullpunktX, nullpunktY);
                    if (interiorType.contains("TABLE"))
                        elemente.add(new Tisch(id, ebene, nurPersonal, RismapsSchluessel.von(Tischart.class, interiorType, Tischart.SONSTIGE), umriss));
                    else
                        elemente.add(new Einbau(id, ebene, nurPersonal, RismapsSchluessel.von(Einbauart.class, interiorType, Einbauart.SONSTIGE), umriss));
                }
                case "OPENING" -> {
                    var linie = geometrie.path("coordinates").get(0);
                    elemente.add(new Tuer(id, ebene, nurPersonal, RismapsSchluessel.von(Tuerart.class, text(p, "type"), Tuerart.TUER),
                            punkt(linie.get(0), nullpunktX, nullpunktY), punkt(linie.get(linie.size() - 1), nullpunktX, nullpunktY)));
                }
                case "POI" -> {
                    var poiType = String.valueOf(text(p, "poiType"));
                    var ausstattungsart = poiType.startsWith("DRAWING_AID") ? Ausstattungsart.ZEICHENHILFE
                            : RismapsSchluessel.von(Ausstattungsart.class, poiType, Ausstattungsart.SONSTIGE);
                    elemente.add(new Ausstattung(id, ebene, nurPersonal, ausstattungsart, text(p, "poiName"),
                            punkt(geometrie.path("coordinates"), nullpunktX, nullpunktY)));
                }
                default -> {
                    /*  Unbekannte Feature-Arten werden übergangen  */
                }
            }
        }
        if (kontur == null)
            throw new IllegalArgumentException("Kein VEHICLE-Feature mit Kontur: " + kennung);
        return new Wagenlayout(kennung, runden(laenge), runden(breite), kontur, elemente);
    }

    private static Sitzplatz sitzplatz(String id, Ebene ebene, boolean nurPersonal, JsonNode p, Umriss umriss) {
        /*  Personalsitze haben keine Platznummer (0)  */
        var nummerText = String.valueOf(text(p, "number")).trim();
        int nummer;
        try {
            nummer = nummerText.isEmpty() || "null".equals(nummerText) ? 0 : Integer.parseInt(nummerText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Sitzplatznummer ist keine Zahl: " + id + " / " + nummerText, e);
        }

        var merkmale = EnumSet.noneOf(Sitzmerkmal.class);
        if (p.path("window").asBoolean(false)) merkmale.add(Sitzmerkmal.FENSTER);
        if (p.path("aisle").asBoolean(false)) merkmale.add(Sitzmerkmal.GANG);
        if (p.path("powersocket").asBoolean(false)) merkmale.add(Sitzmerkmal.STECKDOSE);
        if (p.path("light").asBoolean(false)) merkmale.add(Sitzmerkmal.LESELEUCHTE);

        var bauart = "FOLDING_SEAT".equals(text(p, "seatType")) ? Bauart.KLAPPSITZ
                : RismapsSchluessel.von(Bauart.class, text(p, "constructionType"), Bauart.EINZEL);
        return new Sitzplatz(id, ebene, nurPersonal, nummer, RismapsSchluessel.von(Klasse.class, text(p, "seatClass"), Klasse.KEINE), bauart,
                p.path("orientation").asInt(0), RismapsSchluessel.von(Zone.class, text(p, "zoneType"), Zone.STANDARD), merkmale, umriss);
    }

    /*  Polygon und MultiPolygon: alle Ringe aller Polygone  */
    private static Umriss umriss(JsonNode geometrie, double nullpunktX, double nullpunktY) {
        var koordinaten = geometrie.path("coordinates");
        Iterable<JsonNode> polygone = "Polygon".equals(geometrie.path("type").asText()) ? List.of(koordinaten) : koordinaten;
        var ringe = new ArrayList<List<Punkt>>();
        for (var polygon : polygone)
            for (var ring : polygon) {
                var punkte = new ArrayList<Punkt>();
                for (var koordinate : ring)
                    punkte.add(punkt(koordinate, nullpunktX, nullpunktY));
                ringe.add(punkte);
            }
        return new Umriss(ringe);
    }

    private static Punkt punkt(JsonNode koordinate, double nullpunktX, double nullpunktY) {
        return new Punkt(runden((koordinate.get(0).asDouble() - nullpunktX) * METER_JE_GRAD),
                runden((nullpunktY - koordinate.get(1).asDouble()) * METER_JE_GRAD));
    }

    /*  Auf Zentimeter runden  */
    private static double runden(double meter) {
        return Math.round(meter * 100) / 100.0;
    }

    private static String text(JsonNode knoten, String feld) {
        var wert = knoten.path(feld);
        return wert.isMissingNode() || wert.isNull() ? null : wert.asText();
    }
}
