package kreyj.sitzplatzreservierung.layout;

import kreyj.sitzplatzreservierung.layout.Sitzplanzeichner.PlatzBezug;
import kreyj.sitzplatzreservierung.service.PlatzAbleitung;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SitzplanzeichnerTest {

    private static Wagenlayout layout(String kennung) {
        return RismapsImportTest.lade(kennung + ".json");
    }

    private static Map<String, PlatzBezug> bezuege(Wagenlayout layout) {
        return PlatzAbleitung.ableiten("W", layout).stream()
                .collect(Collectors.toMap(PlatzAbleitung.Platz::elementId, (p) -> new PlatzBezug(p.id(), p.bezeichnung(), p.art().name().toLowerCase())));
    }

    private static Set<String> schichten(Wagenansicht ansicht) {
        return ansicht.formen().stream().map((f) -> f.stile().getFirst()).collect(Collectors.toSet());
    }

    @Test
    void testNurUmrissZonenTischePlaetzeUndIcons() {
        for (var kennung : new String[] { "Beispiel-Klein", "Beispiel-Mittel", "Beispiel-Gross" }) {
            var layout = layout(kennung);
            var ansicht = Sitzplanzeichner.zeichne(layout, bezuege(layout));

            assertEquals(Set.of("schicht-kontur", "schicht-zone", "schicht-tisch", "schicht-platz", "schicht-icon"), schichten(ansicht), kennung);
            assertEquals(layout.laengeM(), ansicht.breiteM());
            assertEquals("schicht-kontur", ansicht.formen().getFirst().stile().getFirst());
        }
    }

    @Test
    void testPlaetzeTragenDieIdDesPlatzes() {
        var layout = layout("Beispiel-Klein");
        var ansicht = Sitzplanzeichner.zeichne(layout, bezuege(layout));
        var plaetze = ansicht.formen().stream().filter((f) -> f.stile().contains("platz")).toList();

        /*  26 Sitze und der Rollstuhlplatz; der Personalsitz wird nicht gezeichnet  */
        assertEquals(27, plaetze.size());
        assertTrue(plaetze.stream().anyMatch((f) -> f.id().equals("W-1") && f.details().get("Platz").equals("1") && f.stile().contains("platz-sitz")));
        var rollstuhl = plaetze.stream().filter((f) -> f.id().equals("W-RS1")).findFirst().orElseThrow();
        assertEquals(Piktogrammart.ROLLSTUHL, rollstuhl.piktogramm().art());
        assertTrue(ansicht.formen().stream().noneMatch((f) -> f.id().endsWith("/lehne")), "keine angedeuteten Lehnen");
    }

    @Test
    void testSitzeOhnePlatzSindNichtReservierbar() {
        var ansicht = Sitzplanzeichner.zeichne(layout("Beispiel-Klein"), Map.of());

        assertTrue(ansicht.formen().stream().filter((f) -> f.stile().contains("platz")).allMatch((f) -> f.stile().contains("platz-gesperrt") || f.stile().contains("platz-rollstuhl")));
        assertFalse(ansicht.formen().stream().anyMatch((f) -> f.id().startsWith("W-")));
    }

    @Test
    void testPersonalWirdNichtGezeichnet() {
        var layout = layout("Beispiel-Mittel");
        var ansicht = Sitzplanzeichner.zeichne(layout, bezuege(layout));
        var personal = layout.elemente().stream().filter(Wagenelement::nurPersonal).map(Wagenelement::id).collect(Collectors.toSet());

        assertFalse(personal.isEmpty());
        assertTrue(ansicht.formen().stream().noneMatch((f) -> personal.contains(f.id())));
    }

    private static Form form(Wagenansicht ansicht, String praefix, Ebene ebene) {
        return ansicht.formen().stream().filter((f) -> f.id().equals(praefix + "/" + ebene)).findFirst().orElse(null);
    }

    private static long anzahl(String text, char zeichen) {
        return text.chars().filter((c) -> c == zeichen).count();
    }

    @Test
    void testLeereWagenendenWerdenJeEbeneWeggelassen() {
        for (var kennung : new String[] { "Beispiel-Klein", "Beispiel-Mittel", "Beispiel-Gross", "I8080-402" }) {
            var layout = layout(kennung);
            var ansicht = Sitzplanzeichner.zeichne(layout, bezuege(layout));

            assertEquals(layout.ebenen(), ansicht.ausschnitte().stream().map(Wagenansicht.Ausschnitt::ebene).toList(), kennung);
            for (var ausschnitt : ansicht.ausschnitte()) {
                assertTrue(ausschnitt.vonXM() >= 0 && ausschnitt.vonXM() < ausschnitt.bisXM() && ausschnitt.bisXM() <= layout.laengeM(), kennung);
                assertTrue(form(ansicht, "kontur", ausschnitt.ebene()) != null && form(ansicht, "kontur-linie", ausschnitt.ebene()) != null, kennung);
            }
        }
    }

    @Test
    void testKuerzungAmWagenendeMitGepunkteterBruecke() {
        var layout = layout("Beispiel-Mittel");
        var ansicht = Sitzplanzeichner.zeichne(layout, bezuege(layout));
        var ausschnitt = ansicht.ausschnitte().getFirst();

        /*  Links liegen Plätze am Wagenende (keine Kürzung), rechts fallen etwa vier Meter leere Fläche weg  */
        assertEquals(0, ausschnitt.vonXM());
        assertTrue(ausschnitt.bisXM() < layout.laengeM() - 1);
        var bruecke = form(ansicht, "kontur-bruecke", ausschnitt.ebene());
        assertEquals(2, anzahl(bruecke.pfad(), 'M'), "je eine Brücke für obere und untere Umrisslinie");
        assertTrue(bruecke.stile().contains("kontur-bruecke"));

        /*  Die Umrisslinie endet am Schnitt, der Wagen wird dort nicht geschlossen  */
        var maxX = ansicht.formen().stream().filter((f) -> f.id().equals("kontur-linie/" + ausschnitt.ebene())).findFirst().orElseThrow().pfad();
        assertFalse(maxX.contains("L" + layout.laengeM()), "die Wagenspitze ist nicht mehr gezeichnet");
    }

    @Test
    void testDoppelstockHatJeEbeneEinenEigenenAusschnitt() {
        var layout = layout("Beispiel-Gross");
        var ansicht = Sitzplanzeichner.zeichne(layout, bezuege(layout));

        assertEquals(3, ansicht.ausschnitte().size());
        for (var ausschnitt : ansicht.ausschnitte()) {
            assertTrue(ausschnitt.vonXM() > 5 && ausschnitt.bisXM() < layout.laengeM() - 5, ausschnitt.toString());
            assertEquals(4, anzahl(form(ansicht, "kontur-bruecke", ausschnitt.ebene()).pfad(), 'M'), "zwei Schnitte mit je zwei Umrisslinien");
        }
        assertTrue(ansicht.ausschnitte().stream().map(Wagenansicht.Ausschnitt::vonXM).distinct().count() > 1);
    }
}
