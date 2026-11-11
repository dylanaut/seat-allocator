package kreyj.sitzplatzreservierung.service;

import jakarta.enterprise.context.ApplicationScoped;
import kreyj.sitzplatzreservierung.exception.WagenlayoutNichtGefundenException;
import kreyj.sitzplatzreservierung.layout.RismapsImport;
import kreyj.sitzplatzreservierung.layout.Wagenlayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kennt die Wagenlayouts aus den RISMAPS-Dateien im Klassenpfad (Kennung = Dateiname ohne ".json") und lädt sie bei Bedarf.
 * Die Kennungen stehen in der Datei index.txt neben den Layouts, da sich Verzeichnisse in einem Jar nicht zuverlässig auflisten lassen.
 */
@ApplicationScoped
public class WagenlayoutKatalog {

    static final String VERZEICHNIS = "rismaps/layouts/by-layout";

    private final Map<String, Wagenlayout> geladen = new ConcurrentHashMap<>();
    private volatile List<String> kennungen;

    /*  Nur für Tests: anderer Klassenlader statt des Kontext-Klassenladers  */
    ClassLoader klassenlader;

    public List<String> kennungen() {
        if (kennungen == null)
            kennungen = lesen();
        return kennungen;
    }

    public Wagenlayout layout(String kennung) {
        /*  Nur Kennungen aus dem Index werden geladen, damit keine beliebigen Pfade gelesen werden  */
        if (!kennungen().contains(kennung))
            throw new WagenlayoutNichtGefundenException(kennung);
        return geladen.computeIfAbsent(kennung, this::laden);
    }

    private Wagenlayout laden(String kennung) {
        try (var json = lader().getResourceAsStream(VERZEICHNIS + "/" + kennung + ".json")) {
            return RismapsImport.importiere(kennung, json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> lesen() {
        try (var index = lader().getResourceAsStream(VERZEICHNIS + "/index.txt")) {
            if (index == null)
                return List.of();
            return new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8)).lines()
                    .map(String::strip).filter((zeile) -> !zeile.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ClassLoader lader() {
        return klassenlader != null ? klassenlader : Thread.currentThread().getContextClassLoader();
    }
}
