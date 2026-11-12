package kreyj.sitzplatzreservierung.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.sitzplatzreservierung.dto.WagenAnlegenRequest;
import kreyj.sitzplatzreservierung.exception.UngueltigeAnfrageException;
import kreyj.sitzplatzreservierung.model.Gruppenkontingent;
import kreyj.sitzplatzreservierung.model.Sitzplatz;
import kreyj.sitzplatzreservierung.model.Wagen;

import java.time.Instant;
import java.util.List;

/**
 * Legt Wagen an, deren Sitzplätze aus einem Wagenlayout stammen.
 */
@ApplicationScoped
public class WagenAnlage {

    static final List<String> DEMO_HALTE = List.of("Hamburg", "Hannover", "Frankfurt", "München");
    static final Instant KONTINGENT_FREIGABEFRIST = Instant.parse("2030-01-01T00:00:00Z");

    @Inject
    WagenlayoutKatalog katalog;

    @Transactional
    public Wagen anlegen(WagenAnlegenRequest anfrage) {
        var layout = katalog.layout(anfrage.layout());
        var id = anfrage.id() != null ? anfrage.id() : layout.kennung();
        if (Wagen.findById(id) != null)
            throw new UngueltigeAnfrageException("Wagen existiert bereits: " + id);

        var wagen = new Wagen();
        wagen.id = id;
        wagen.layout = layout.kennung();
        wagen.zugnummer = anfrage.zugnummer() != null ? anfrage.zugnummer() : "Demo " + layout.kennung();
        wagen.halte = String.join(",", anfrage.halte() != null ? anfrage.halte() : DEMO_HALTE);
        wagen.persist();

        var plaetze = PlatzAbleitung.ableiten(id, layout);
        var kontingentId = id + "-GRP-01";
        if (anfrage.gruppenkontingent() && plaetze.stream().anyMatch(PlatzAbleitung.Platz::kontingent)) {
            var kontingent = new Gruppenkontingent();
            kontingent.id = kontingentId;
            kontingent.wagenId = id;
            kontingent.freigabefrist = KONTINGENT_FREIGABEFRIST;
            kontingent.persist();
        } else
            kontingentId = null;

        for (var platz : plaetze) {
            var sitz = new Sitzplatz();
            sitz.id = platz.id();
            sitz.wagenId = id;
            sitz.art = platz.art();
            sitz.reihe = platz.reihe();
            sitz.spalte = platz.spalte();
            sitz.gangseite = platz.gangseite();
            sitz.zone = platz.zone();
            sitz.bezeichnung = platz.bezeichnung();
            sitz.elementId = platz.elementId();
            sitz.kontingentId = platz.kontingent() ? kontingentId : null;
            sitz.persist();
        }
        return wagen;
    }
}
