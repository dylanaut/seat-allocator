package kreyj.sitzplatzreservierung.layout;

import kreyj.sitzplatzreservierung.layout.Wagenelement.Ausstattung;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Sitzplatz;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tisch;
import kreyj.sitzplatzreservierung.layout.Wagenelement.Tuer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RismapsImportTest {

    static final String VERZEICHNIS = "/rismaps/layouts/by-layout";

    static Wagenlayout lade(String datei) {
        try (InputStream json = RismapsImportTest.class.getResourceAsStream(VERZEICHNIS + "/" + datei)) {
            return RismapsImport.importiere(datei, json);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void testBeispielwagenWirdVollstaendigUebernommen() {
        var layout = lade("Beispiel-Klein.json");

        assertEquals(13.3, layout.laengeM(), .05);
        assertEquals(2.9, layout.breiteM(), .05);
        assertEquals(27, layout.sitzplaetze().size());
        assertEquals(5, layout.elemente(Tisch.class).size());
        assertEquals(9, layout.elemente(Tuer.class).size());
        assertEquals(List.of(Ebene.HAUPTEBENE), layout.ebenen());
        assertFalse(layout.kontur().ringe().isEmpty());
    }

    @Test
    void testSitzmerkmaleUndKoordinatenInMetern() {
        var sitz = lade("Beispiel-Klein.json").sitzplaetze().stream().filter((s) -> s.nummer() == 4).findFirst().orElseThrow();

        assertEquals(Klasse.ZWEITE, sitz.klasse());
        assertEquals(Bauart.DOPPEL, sitz.bauart());
        assertEquals(Zone.STANDARD, sitz.zone());
        assertEquals(270, sitz.lehnenrichtungGrad());
        assertEquals(Set.of(Sitzmerkmal.FENSTER, Sitzmerkmal.STECKDOSE, Sitzmerkmal.LESELEUCHTE), sitz.merkmale());

        /*  Sitz 4 sitzt am Fenster bei x = 6,3 bis 6,9 m und y = 0,17 bis 0,64 m (Nullpunkt oben links)  */
        var mitte = sitz.umriss().mitte();
        assertEquals(6.61, mitte.x(), .05);
        assertEquals(.4, mitte.y(), .05);
    }

    @Test
    void testPoiZeichenhilfenWerdenErkannt() {
        var ausstattungen = lade("Beispiel-Klein.json").elemente(Ausstattung.class);

        assertTrue(ausstattungen.stream().anyMatch((a) -> a.art() == Wagenelement.Ausstattungsart.MONITOR));
        assertTrue(ausstattungen.stream().anyMatch((a) -> a.art() == Wagenelement.Ausstattungsart.ZEICHENHILFE));
    }

    @Test
    void testDoppelstockwagenHatDreiEbenen() {
        var layout = lade("Beispiel-Gross.json");

        assertEquals(List.of(Ebene.OBERDECK, Ebene.HAUPTEBENE, Ebene.UNTERDECK), layout.ebenen());
    }

    @Test
    void testAlleLayoutsLassenSichImportieren() throws IOException, URISyntaxException {
        var verzeichnis = Path.of(RismapsImportTest.class.getResource(VERZEICHNIS).toURI());
        try (Stream<Path> dateien = Files.list(verzeichnis)) {
            var json = dateien.filter((d) -> d.getFileName().toString().endsWith(".json")).toList();
            assertEquals(4, json.size());
            for (var datei : json) {
                var layout = lade(datei.getFileName().toString());
                assertFalse(layout.sitzplaetze().isEmpty(), datei.toString());
                assertTrue(layout.laengeM() > 10, datei.toString());
            }
        }
    }
}
