package kreyj.sitzplatzreservierung.layout;

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
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die drei Beispiel-Layouts (klein, mittel, groß) sollen zusammen alle Elementarten, Merkmale und Piktogramme des Modells zeigen.
 */
class BeispielLayoutsTest {

    private static final List<Wagenlayout> LAYOUTS = Stream.of("Beispiel-Klein.json", "Beispiel-Mittel.json", "Beispiel-Gross.json")
            .map(RismapsImportTest::lade).toList();

    private static <T extends Wagenelement, E extends Enum<E>> Set<E> vorkommende(Class<T> art, Function<T, E> merkmal) {
        return LAYOUTS.stream().flatMap((layout) -> layout.elemente(art).stream()).map(merkmal).collect(Collectors.toSet());
    }

    private static <E extends Enum<E>> Set<E> fehlende(Class<E> art, Set<E> vorhanden, E... ausgenommen) {
        var fehlend = EnumSet.allOf(art);
        fehlend.removeAll(vorhanden);
        fehlend.removeAll(Arrays.asList(ausgenommen));
        return fehlend;
    }

    @Test
    void testGroessenKleinMittelGross() {
        assertEquals(List.of("Beispiel-Klein", "Beispiel-Mittel", "Beispiel-Gross"), LAYOUTS.stream().map(Wagenlayout::kennung)
                .map((kennung) -> kennung.replace(".json", "")).toList());
        assertTrue(LAYOUTS.get(0).laengeM() < LAYOUTS.get(1).laengeM());
        assertTrue(LAYOUTS.get(0).sitzplaetze().size() < LAYOUTS.get(1).sitzplaetze().size());
        assertTrue(LAYOUTS.get(1).sitzplaetze().size() < LAYOUTS.get(2).sitzplaetze().size());
    }

    @Test
    void testAlleElementartenKommenVor() {
        assertEquals(Set.of(), fehlende(Tischart.class, vorkommende(Tisch.class, Tisch::art), Tischart.SONSTIGE));
        assertEquals(Set.of(), fehlende(Einbauart.class, vorkommende(Einbau.class, Einbau::art), Einbauart.SONSTIGE));
        assertEquals(Set.of(), fehlende(Bereichsart.class, vorkommende(Bereich.class, Bereich::art), Bereichsart.SONSTIGE));
        assertEquals(Set.of(), fehlende(Tuerart.class, vorkommende(Tuer.class, Tuer::art)));
        assertEquals(Set.of(), fehlende(Ausstattungsart.class, vorkommende(Ausstattung.class, Ausstattung::art), Ausstattungsart.SONSTIGE));
    }

    @Test
    void testAlleSitzmerkmaleZonenKlassenBauartenUndEbenenKommenVor() {
        assertEquals(Set.of(), fehlende(Klasse.class, vorkommende(Sitzplatz.class, Sitzplatz::klasse)));
        assertEquals(Set.of(), fehlende(Bauart.class, vorkommende(Sitzplatz.class, Sitzplatz::bauart)));

        var zonen = vorkommende(Sitzplatz.class, Sitzplatz::zone);
        zonen.addAll(vorkommende(Zonenflaeche.class, Zonenflaeche::zone));
        assertEquals(Set.of(), fehlende(Zone.class, zonen));

        var merkmale = LAYOUTS.stream().flatMap((layout) -> layout.sitzplaetze().stream()).flatMap((sitz) -> sitz.merkmale().stream()).collect(Collectors.toSet());
        assertEquals(Set.of(), fehlende(Sitzmerkmal.class, merkmale));

        var ebenen = LAYOUTS.stream().flatMap((layout) -> layout.ebenen().stream()).collect(Collectors.toSet());
        assertEquals(Set.of(), fehlende(Ebene.class, ebenen));
        assertTrue(LAYOUTS.stream().flatMap((layout) -> layout.elemente(Sitzplatz.class).stream()).anyMatch((sitz) -> sitz.nurPersonal() && sitz.nummer() == 0));
    }

    @Test
    void testAllePiktogrammeWerdenGezeichnet() {
        var gezeichnet = LAYOUTS.stream().flatMap((layout) -> Wagenzeichner.zeichne(layout, null, null, Sitzfaerbung.STANDARD).formen().stream())
                .map(Form::piktogramm).filter((p) -> p != null).map(Form.Piktogramm::art).collect(Collectors.toSet());

        assertEquals(Set.of(), fehlende(Piktogrammart.class, gezeichnet));
    }
}
