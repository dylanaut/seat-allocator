package kreyj.sitzplatzreservierung.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WagenzeichnerTest {

    private static final Wagenlayout LAYOUT = RismapsImportTest.lade("Beispiel-Klein.json");

    private static Form sitz(Wagenansicht ansicht, int nummer) {
        return ansicht.formen().stream().filter((f) -> f.id().equals(idVon(nummer))).findFirst().orElseThrow();
    }

    private static String idVon(int nummer) {
        return LAYOUT.sitzplaetze().stream().filter((s) -> s.nummer() == nummer).findFirst().orElseThrow().id();
    }

    @Test
    void testJederSitzWirdMitLehneGezeichnet() {
        var ansicht = Wagenzeichner.zeichne(LAYOUT, null, null, Sitzfaerbung.STANDARD);

        var sitze = ansicht.formen().stream().filter((f) -> f.stile().contains("sitz")).toList();
        var lehnen = ansicht.formen().stream().filter((f) -> f.stile().contains("sitz-lehne")).toList();
        assertEquals(27, sitze.size());
        assertEquals(27, lehnen.size());
        assertEquals(LAYOUT.sitzplaetze().stream().filter((s) -> s.nummer() > 0).count(),
                sitze.stream().filter((f) -> f.beschriftung() != null).count());
        assertEquals(13.3, ansicht.breiteM(), .05);
    }

    @Test
    void testPfadeSindGueltigeZahlen() {
        var ansicht = Wagenzeichner.zeichne(LAYOUT, null, null, Sitzfaerbung.STANDARD);

        for (var form : ansicht.formen()) {
            assertFalse(form.pfad().contains("NaN"), form.id());
            assertFalse(form.pfad().contains("Infinity"), form.id());
            assertTrue(form.pfad().isEmpty() || form.pfad().startsWith("M"), form.id());
        }
    }

    @Test
    void testRueckenlehneLiegtAnDerSeiteDerLehnenrichtung() {
        /*  Sitz 3 reicht von x = 4,76 bis 5,34; bei 90 Grad liegt die Lehne an der Seite mit kleinem x und ist nur ein Teil der Sitztiefe  */
        var ansicht = Wagenzeichner.zeichne(LAYOUT, null, null, Sitzfaerbung.STANDARD);
        var lehne = ansicht.formen().stream().filter((f) -> f.id().equals(idVon(3) + "/lehne")).findFirst().orElseThrow();

        var xWerte = java.util.Arrays.stream(lehne.pfad().replace("Z", "").split("[ML]")).filter((s) -> !s.isEmpty())
                .mapToDouble((p) -> Double.parseDouble(p.split(" ")[0])).toArray();
        assertEquals(4.76, java.util.Arrays.stream(xWerte).min().orElseThrow(), .02);
        assertTrue(java.util.Arrays.stream(xWerte).max().orElseThrow() < 5.2);
    }

    @Test
    void testZustandFuerGewaehltenFahrtabschnitt() {
        var plan = Belegungsplan.leer("Z1", 1).mit(new Belegung("R1", 3, new Fahrtabschnitt(0, 2), BelegungsArt.RESERVIERT));

        var teilweise = Wagenzeichner.zeichne(LAYOUT, plan, new Fahrtabschnitt(1, 3), Sitzfaerbung.STANDARD);
        var belegt = Wagenzeichner.zeichne(LAYOUT, plan, new Fahrtabschnitt(0, 2), Sitzfaerbung.STANDARD);
        var frei = Wagenzeichner.zeichne(LAYOUT, plan, new Fahrtabschnitt(2, 3), Sitzfaerbung.STANDARD);

        assertTrue(sitz(teilweise, 3).stile().contains("zustand-teilweise"));
        assertTrue(sitz(belegt, 3).stile().contains("zustand-belegt"));
        assertTrue(sitz(frei, 3).stile().contains("zustand-frei"));
        assertTrue(sitz(belegt, 5).stile().contains("zustand-frei"));
        assertEquals("BELEGT", sitz(belegt, 3).details().get("Zustand"));
    }

    @Test
    void testFaerbungNachKlasseUndBereich() {
        var layout = RismapsImportTest.lade("Beispiel-Mittel.json");

        var ruhe = Wagenzeichner.zeichne(layout, null, null, Sitzfaerbung.BEREICH).formen().stream()
                .anyMatch((f) -> f.stile().contains("sitz") && f.stile().contains("farbe-ruhe"));
        var erste = Wagenzeichner.zeichne(layout, null, null, Sitzfaerbung.KLASSE).formen().stream()
                .anyMatch((f) -> f.stile().contains("sitz") && f.stile().contains("farbe-erste"));
        var standard = Wagenzeichner.zeichne(layout, null, null, Sitzfaerbung.STANDARD).formen().stream()
                .anyMatch((f) -> f.stile().stream().anyMatch((s) -> s.startsWith("farbe-")));

        assertTrue(ruhe);
        assertTrue(erste);
        assertFalse(standard);
    }

    @Test
    void testSchichtenInZeichenreihenfolge() {
        var ansicht = Wagenzeichner.zeichne(LAYOUT, null, null, Sitzfaerbung.STANDARD);

        assertEquals("schicht-kontur", ansicht.formen().getFirst().stile().getFirst());
        var letzteSitzposition = ansicht.formen().stream().filter((f) -> f.stile().contains("schicht-sitz")).count();
        assertEquals(54, letzteSitzposition);
        var ersteTuer = ansicht.formen().indexOf(ansicht.formen().stream().filter((f) -> f.stile().contains("schicht-tuer")).findFirst().orElseThrow());
        var letzterSitz = ansicht.formen().lastIndexOf(ansicht.formen().stream().filter((f) -> f.stile().contains("schicht-sitz")).reduce((a, b) -> b).orElseThrow());
        assertTrue(ersteTuer > letzterSitz);
    }
}
