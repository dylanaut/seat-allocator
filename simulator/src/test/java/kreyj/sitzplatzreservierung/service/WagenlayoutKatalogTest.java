package kreyj.sitzplatzreservierung.service;

import kreyj.sitzplatzreservierung.exception.WagenlayoutNichtGefundenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WagenlayoutKatalogTest {

    @Test
    void testLayoutsAusDemKlassenpfad() {
        var katalog = new WagenlayoutKatalog();

        assertEquals(3, katalog.kennungen().size());
        assertTrue(katalog.kennungen().contains("Beispiel-Klein"));
        assertEquals(27, katalog.layout("Beispiel-Klein").sitzplaetze().size());
        assertTrue(katalog.layout("Beispiel-Klein") == katalog.layout("Beispiel-Klein"), "wird nur einmal geladen");
    }

    @Test
    void testIndexEntsprichtDenLayoutDateien() throws IOException, java.net.URISyntaxException {
        var verzeichnis = Path.of(getClass().getResource("/" + WagenlayoutKatalog.VERZEICHNIS).toURI());
        try (var dateien = java.nio.file.Files.list(verzeichnis)) {
            var vorhanden = dateien.map((d) -> d.getFileName().toString()).filter((n) -> n.endsWith(".json"))
                    .map((n) -> n.substring(0, n.length() - ".json".length())).sorted().toList();

            assertEquals(vorhanden, new WagenlayoutKatalog().kennungen().stream().sorted().toList());
        }
    }

    @Test
    void testUnbekannteKennungUndPfadangabenWerdenAbgelehnt() {
        var katalog = new WagenlayoutKatalog();

        assertThrows(WagenlayoutNichtGefundenException.class, () -> katalog.layout("gibt-es-nicht"));
        assertThrows(WagenlayoutNichtGefundenException.class, () -> katalog.layout("../anderes/x"));
    }

    @Test
    void testLayoutsAusEinemJar(@TempDir Path verzeichnis) throws IOException {
        /*  Wie im Demo-Uber-Jar: Layouts liegen in einem Jar ohne Verzeichniseinträge  */
        var jar = verzeichnis.resolve("layouts.jar");
        try (var ausgabe = new JarOutputStream(java.nio.file.Files.newOutputStream(jar));
             InputStream quelle = getClass().getResourceAsStream("/rismaps/layouts/by-layout/Beispiel-Klein.json")) {
            var inhalt = quelle.readAllBytes();
            ausgabe.putNextEntry(new JarEntry(WagenlayoutKatalog.VERZEICHNIS + "/index.txt"));
            ausgabe.write("A-Wagen\nB-Wagen\n".getBytes());
            ausgabe.closeEntry();
            for (var name : List.of("B-Wagen", "A-Wagen")) {
                ausgabe.putNextEntry(new JarEntry(WagenlayoutKatalog.VERZEICHNIS + "/" + name + ".json"));
                ausgabe.write(inhalt);
                ausgabe.closeEntry();
            }
        }

        try (var lader = new URLClassLoader(new URL[] { jar.toUri().toURL() }, null)) {
            var katalog = new WagenlayoutKatalog();
            katalog.klassenlader = lader;

            assertEquals(List.of("A-Wagen", "B-Wagen"), katalog.kennungen());
            assertEquals(27, katalog.layout("A-Wagen").sitzplaetze().size());
        }
    }
}
