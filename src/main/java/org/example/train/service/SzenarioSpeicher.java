package org.example.train.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.train.dto.Schrittart;
import org.example.train.dto.Szenario;
import org.example.train.dto.SzenarioUebersicht;
import org.example.train.dto.Szenarioschritt;
import org.example.train.exception.SzenarioNichtGefundenException;
import org.example.train.exception.UngueltigeAnfrageException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Speichert Szenarien als JSON-Dateien (eine Datei je Szenario, Dateiname = Szenario-Id) in einem Verzeichnis.
 */
@ApplicationScoped
public class SzenarioSpeicher {

    private static final Pattern GUELTIGE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    @ConfigProperty(name = "zuteilung.szenarien.verzeichnis", defaultValue = "szenarien")
    String verzeichnis;

    @Inject
    ObjectMapper objectMapper;

    private Path pfad(String id) {
        if (id == null || !GUELTIGE_ID.matcher(id).matches()) {
            throw new UngueltigeAnfrageException("Ungültige Szenario-Id: " + id);
        }
        return Path.of(verzeichnis).resolve(id + ".json");
    }

    /**
     * Erzeugt aus dem Namen eine Datei-taugliche Id (Kleinbuchstaben, Ziffern, Bindestrich).
     */
    static String slug(String name) {
        String s = Normalizer.normalize(name.toLowerCase().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss"),
                Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        s = s.length() > 64 ? s.substring(0, 64) : s;
        return s.isEmpty() ? "szenario" : s;
    }

    /**
     * Prüft die Schritte: Anfrageschritte brauchen eine Anfrage, Stornoschritte verweisen auf einen früheren Anfrageschritt.
     */
    private void pruefen(Szenario szenario) {
        List<Szenarioschritt> schritte = szenario.schritte();
        for (int i = 0; i < schritte.size(); i++) {
            Szenarioschritt schritt = schritte.get(i);
            int nummer = i + 1;
            if (schritt.typ() == Schrittart.ANFRAGE && schritt.anfrage() == null) {
                throw new UngueltigeAnfrageException("Schritt " + nummer + ": Anfrage fehlt.");
            }
            if (schritt.typ() == Schrittart.STORNO) {
                Integer bezug = schritt.bezugSchritt();
                if (bezug == null || bezug >= nummer || schritte.get(bezug - 1).typ() != Schrittart.ANFRAGE) {
                    throw new UngueltigeAnfrageException("Schritt " + nummer + ": Storno braucht einen früheren Anfrageschritt.");
                }
            }
        }
    }

    private void schreiben(String id, Szenario szenario) {
        try {
            Files.createDirectories(Path.of(verzeichnis));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(pfad(id).toFile(),
                    new Szenario(id, szenario.name(), szenario.beschreibung(), szenario.wagenId(), szenario.schritte()));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<SzenarioUebersicht> auflisten() {
        Path dir = Path.of(verzeichnis);
        if (!Files.isDirectory(dir)) return List.of();

        try (Stream<Path> dateien = Files.list(dir)) {
            List<SzenarioUebersicht> ergebnis = new ArrayList<>();
            for (Path datei : dateien.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String id = datei.getFileName().toString().replaceAll("\\.json$", "");
                if (!GUELTIGE_ID.matcher(id).matches()) continue;
                Szenario s = laden(id);
                ergebnis.add(new SzenarioUebersicht(id, s.name(), s.beschreibung(), s.wagenId(), s.schritte().size()));
            }
            ergebnis.sort(Comparator.comparing(SzenarioUebersicht::name, String.CASE_INSENSITIVE_ORDER));
            return ergebnis;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Szenario laden(String id) {
        Path datei = pfad(id);
        if (!Files.isRegularFile(datei)) throw new SzenarioNichtGefundenException(id);

        try {
            Szenario s = objectMapper.readValue(datei.toFile(), Szenario.class);
            return new Szenario(id, s.name(), s.beschreibung(), s.wagenId(), s.schritte());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Legt ein neues Szenario an; die Id wird aus dem Namen abgeleitet und bei Kollision nummeriert.
     */
    public Szenario anlegen(Szenario szenario) {
        pruefen(szenario);
        String basis = slug(szenario.name());
        String id = basis;
        for (int i = 2; Files.exists(pfad(id)); i++) {
            id = basis + "-" + i;
        }
        schreiben(id, szenario);
        return laden(id);
    }

    public Szenario ersetzen(String id, Szenario szenario) {
        if (!Files.isRegularFile(pfad(id))) throw new SzenarioNichtGefundenException(id);
        pruefen(szenario);
        schreiben(id, szenario);
        return laden(id);
    }

    public void loeschen(String id) {
        try {
            if (!Files.deleteIfExists(pfad(id))) throw new SzenarioNichtGefundenException(id);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
