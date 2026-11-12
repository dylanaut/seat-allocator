package kreyj.sitzplatzreservierung.service;

import kreyj.sitzplatzreservierung.model.Platzart;
import kreyj.sitzplatzreservierung.model.Zone;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatzAbleitungTest {

    private static final WagenlayoutKatalog KATALOG = new WagenlayoutKatalog();

    private static List<PlatzAbleitung.Platz> plaetze(String kennung) {
        return PlatzAbleitung.ableiten("W", KATALOG.layout(kennung));
    }

    private static long anzahl(List<PlatzAbleitung.Platz> plaetze, Platzart art) {
        return plaetze.stream().filter((p) -> p.art() == art).count();
    }

    @Test
    void testNurNummerierteSitzeUndSonderplaetzeWerdenPlaetze() {
        var klein = plaetze("Beispiel-Klein");
        /*  27 Sitze im Layout, davon ein Personalsitz ohne Nummer; dazu der Rollstuhlbereich  */
        assertEquals(26, anzahl(klein, Platzart.SITZ));
        assertEquals(1, anzahl(klein, Platzart.ROLLSTUHL));
        assertEquals(0, anzahl(klein, Platzart.FAHRRAD));

        var mittel = plaetze("Beispiel-Mittel");
        assertEquals(48, anzahl(mittel, Platzart.SITZ));
        assertEquals(1, anzahl(mittel, Platzart.FAHRRAD));
        assertEquals(1, anzahl(mittel, Platzart.ROLLSTUHL));

        assertEquals(87, anzahl(plaetze("Beispiel-Gross"), Platzart.SITZ));
        assertEquals(1, anzahl(plaetze("Beispiel-Gross"), Platzart.ROLLSTUHL));
    }

    @Test
    void testIdsSindEindeutigUndTragenDieWagenId() {
        for (var kennung : KATALOG.kennungen()) {
            var plaetze = plaetze(kennung);
            assertEquals(plaetze.size(), plaetze.stream().map(PlatzAbleitung.Platz::id).collect(Collectors.toSet()).size(), kennung);
            assertTrue(plaetze.stream().allMatch((p) -> p.id().startsWith("W-")), kennung);
            assertEquals(plaetze.size(), plaetze.stream().map(PlatzAbleitung.Platz::elementId).collect(Collectors.toSet()).size(), kennung);
        }
        assertEquals("W-RS1", plaetze("Beispiel-Klein").stream().filter((p) -> p.art() == Platzart.ROLLSTUHL).findFirst().orElseThrow().id());
    }

    @Test
    void testZonenAusDemLayout() {
        var mittel = plaetze("Beispiel-Mittel");
        assertEquals(16, mittel.stream().filter((p) -> p.zone() == Zone.RUHE).count());
        /*  Familie (4) und Kleinkind (4) bilden den Kinderbereich  */
        assertEquals(8, mittel.stream().filter((p) -> p.zone() == Zone.KINDER).count());
    }

    @Test
    void testPlaetzeEinerEbeneHabenEigeneReihenUndNurEinePlatzstelleProZelle() {
        for (var kennung : KATALOG.kennungen()) {
            var zellen = new HashSet<String>();
            for (var p : plaetze(kennung))
                if (p.art() == Platzart.SITZ)
                    assertTrue(zellen.add(p.reihe() + "/" + p.spalte()), kennung + " Platz " + p.bezeichnung());
        }

        /*  Doppelstock: die Reihen der Ebenen liegen weit auseinander  */
        var reihen = plaetze("Beispiel-Gross").stream().map(PlatzAbleitung.Platz::reihe).collect(Collectors.toSet());
        assertTrue(reihen.stream().anyMatch((r) -> r < 1000) && reihen.stream().anyMatch((r) -> r >= 1000));
    }

    @Test
    void testGangseiteTrenntDieSitzpaare() {
        /*  Beispiel-Klein: 2+2, links der Gang  */
        var klein = plaetze("Beispiel-Klein").stream().filter((p) -> p.art() == Platzart.SITZ).toList();
        for (var reihe : klein.stream().map(PlatzAbleitung.Platz::reihe).distinct().toList()) {
            var seiten = klein.stream().filter((p) -> p.reihe() == reihe && p.spalte() < 2).map(PlatzAbleitung.Platz::gangseite).collect(Collectors.toSet());
            assertEquals(1, seiten.size(), "Reihe " + reihe);
        }
        assertEquals(2, klein.stream().map(PlatzAbleitung.Platz::gangseite).distinct().count());
    }

    @Test
    void testGruppenkontingentIstEineTischgruppeMitVierSitzen() {
        for (var kennung : List.of("Beispiel-Klein", "Beispiel-Mittel", "Beispiel-Gross"))
            assertEquals(4, plaetze(kennung).stream().filter(PlatzAbleitung.Platz::kontingent).count(), kennung);
    }
}
