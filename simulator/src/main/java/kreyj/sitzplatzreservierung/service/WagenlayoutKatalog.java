package kreyj.sitzplatzreservierung.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import kreyj.sitzplatzreservierung.exception.WagenlayoutNichtGefundenException;
import kreyj.sitzplatzreservierung.layout.RismapsImport;
import kreyj.sitzplatzreservierung.layout.Wagenlayout;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kennt die Wagenlayouts aus den RISMAPS-Dateien im Klassenpfad (Kennung = Dateiname ohne ".json") und lädt sie bei Bedarf.
 * Die Kennungen stehen in der Datei index.txt neben den Layouts, da sich Verzeichnisse in einem Jar nicht zuverlässig auflisten lassen.
 * Zusätzlich werden alle *.json-Dateien im Verzeichnis wagenlayouts.verzeichnis angeboten; bei gleicher Kennung gewinnt die Datei dort.
 */
@ApplicationScoped
public class WagenlayoutKatalog {

    static final String VERZEICHNIS = "rismaps/layouts/by-layout";

    /*  Erlaubte Kennungen: keine Pfadtrenner, kein führender Punkt  */
    private static final Pattern KENNUNG = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*");

    private final Map<String, Wagenlayout> geladen = new ConcurrentHashMap<>();
    private volatile List<String> kennungen;

    private final Optional<String> externesVerzeichnis;

    /*  Nur für Tests: anderer Klassenlader statt des Kontext-Klassenladers  */
    ClassLoader klassenlader;

    /*  Für den CDI-Proxy und für Tests ohne externes Verzeichnis  */
    WagenlayoutKatalog() {
        this(Optional.empty());
    }

    @Inject
    WagenlayoutKatalog(@ConfigProperty(name = "wagenlayouts.verzeichnis") Optional<String> externesVerzeichnis) {
        this.externesVerzeichnis = externesVerzeichnis;
    }

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
        var extern = externeDatei(kennung);
        try (var json = extern != null ? Files.newInputStream(extern) : lader().getResourceAsStream(VERZEICHNIS + "/" + kennung + ".json")) {
            return RismapsImport.importiere(kennung, json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> lesen() {
        var alle = new ArrayList<String>();
        try (var index = lader().getResourceAsStream(VERZEICHNIS + "/index.txt")) {
            if (index != null)
                new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8)).lines()
                        .map(String::strip).filter((zeile) -> !zeile.isEmpty()).forEach(alle::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (externesVerzeichnis.isPresent() && Files.isDirectory(Path.of(externesVerzeichnis.get())))
            try (var dateien = Files.list(Path.of(externesVerzeichnis.get()))) {
                dateien.map((datei) -> datei.getFileName().toString()).filter((name) -> name.endsWith(".json")).sorted()
                        .map((name) -> name.substring(0, name.length() - ".json".length()))
                        .filter((name) -> KENNUNG.matcher(name).matches() && !alle.contains(name)).forEach(alle::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        return List.copyOf(alle);
    }

    /*  Datei im externen Verzeichnis oder null; die Kennung stammt bereits aus dem Index  */
    private Path externeDatei(String kennung) {
        if (externesVerzeichnis.isEmpty())
            return null;
        var datei = Path.of(externesVerzeichnis.get(), kennung + ".json");
        return Files.isRegularFile(datei) ? datei : null;
    }

    private ClassLoader lader() {
        return klassenlader != null ? klassenlader : Thread.currentThread().getContextClassLoader();
    }
}
