package kreyj.sitzplatzreservierung.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.sitzplatzreservierung.dto.LayoutBelegungRequest;
import kreyj.sitzplatzreservierung.dto.LayoutUebersicht;
import kreyj.sitzplatzreservierung.exception.UngueltigeAnfrageException;
import kreyj.sitzplatzreservierung.layout.Belegung;
import kreyj.sitzplatzreservierung.layout.BelegungsArt;
import kreyj.sitzplatzreservierung.layout.Fahrtabschnitt;
import kreyj.sitzplatzreservierung.layout.Piktogrammart;
import kreyj.sitzplatzreservierung.layout.Sitzfaerbung;
import kreyj.sitzplatzreservierung.layout.Wagenansicht;
import kreyj.sitzplatzreservierung.layout.Wagenzeichner;
import kreyj.sitzplatzreservierung.service.BelegungsSpeicher;
import kreyj.sitzplatzreservierung.service.WagenlayoutKatalog;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wagenlayouts aus den RISMAPS-Dateien als fertig gezeichnete Ansicht, mit Belegung je Fahrtabschnitt einer Zugfahrt.
 */
@Path("/api/v1/wagenlayouts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WagenlayoutResource {

    static final String STANDARD_ZUGFAHRT = "demo";

    @Inject
    WagenlayoutKatalog katalog;

    @Inject
    BelegungsSpeicher speicher;

    @GET
    public List<LayoutUebersicht> auflisten() {
        return katalog.kennungen().stream().map((kennung) -> {
            var layout = katalog.layout(kennung);
            return new LayoutUebersicht(kennung, layout.laengeM(), layout.breiteM(), layout.sitzplaetze().size(), layout.ebenen());
        }).toList();
    }

    /**
     * SVG-Inhalt der Piktogramme (24er-Raster) nach Name, wie in den Formen der Ansicht referenziert.
     */
    @GET
    @Path("/piktogramme")
    public Map<String, String> piktogramme() {
        var piktogramme = new LinkedHashMap<String, String>();
        Arrays.stream(Piktogrammart.values()).forEach((art) -> piktogramme.put(art.name(), art.svg()));
        return piktogramme;
    }

    @GET
    @Path("/halte")
    public List<String> halte(@QueryParam("zugfahrt") @DefaultValue(STANDARD_ZUGFAHRT) String zugfahrt) {
        return speicher.halte(zugfahrt);
    }

    /**
     * Ansicht des Wagens; mit von und nach (Halteindizes) wird der Zustand jedes Sitzplatzes für diesen Fahrtabschnitt gezeichnet.
     */
    @GET
    @Path("/{kennung}/ansicht")
    public Wagenansicht ansicht(
            @PathParam("kennung") String kennung,
            @QueryParam("zugfahrt") @DefaultValue(STANDARD_ZUGFAHRT) String zugfahrt,
            @QueryParam("von") Integer von,
            @QueryParam("nach") Integer nach,
            @QueryParam("faerbung") @DefaultValue("STANDARD") Sitzfaerbung faerbung
    ) {
        var layout = katalog.layout(kennung);
        if ((von == null) != (nach == null))
            throw new UngueltigeAnfrageException("von und nach müssen zusammen angegeben werden");
        var abschnitt = von == null ? null : abschnitt(zugfahrt, von, nach);
        return Wagenzeichner.zeichne(layout, speicher.plan(zugfahrt, kennung), abschnitt, faerbung);
    }

    @GET
    @Path("/{kennung}/belegungen")
    public List<Belegung> belegungen(
            @PathParam("kennung") String kennung,
            @QueryParam("zugfahrt") @DefaultValue(STANDARD_ZUGFAHRT) String zugfahrt
    ) {
        katalog.layout(kennung);
        return speicher.plan(zugfahrt, kennung).belegungen();
    }

    @POST
    @Path("/{kennung}/belegungen")
    public Response belegen(
            @PathParam("kennung") String kennung,
            @QueryParam("zugfahrt") @DefaultValue(STANDARD_ZUGFAHRT) String zugfahrt,
            @Valid LayoutBelegungRequest anfrage
    ) {
        var layout = katalog.layout(kennung);
        if (layout.sitzplaetze().stream().noneMatch((sitz) -> sitz.nummer() == anfrage.sitzNummer()))
            throw new UngueltigeAnfrageException("Platz " + anfrage.sitzNummer() + " existiert im Layout " + kennung + " nicht");

        var reservierungId = anfrage.reservierungId() != null ? anfrage.reservierungId() : "R-" + UUID.randomUUID().toString().substring(0, 8);
        var art = anfrage.art() != null ? anfrage.art() : BelegungsArt.RESERVIERT;
        var belegung = speicher.belegen(zugfahrt, kennung,
                new Belegung(reservierungId, anfrage.sitzNummer(), abschnitt(zugfahrt, anfrage.vonHalt(), anfrage.nachHalt()), art));
        return Response.status(Response.Status.CREATED).entity(belegung).build();
    }

    @DELETE
    @Path("/{kennung}/belegungen/{reservierungId}")
    public Response freigeben(
            @PathParam("kennung") String kennung,
            @PathParam("reservierungId") String reservierungId,
            @QueryParam("zugfahrt") @DefaultValue(STANDARD_ZUGFAHRT) String zugfahrt
    ) {
        katalog.layout(kennung);
        speicher.freigeben(zugfahrt, kennung, reservierungId);
        return Response.noContent().build();
    }

    private Fahrtabschnitt abschnitt(String zugfahrt, int von, int nach) {
        var letzterHalt = speicher.halte(zugfahrt).size() - 1;
        if (nach > letzterHalt)
            throw new UngueltigeAnfrageException("Halteindex " + nach + " liegt hinter dem letzten Halt " + letzterHalt);
        try {
            return new Fahrtabschnitt(von, nach);
        } catch (IllegalArgumentException e) {
            throw new UngueltigeAnfrageException(e.getMessage());
        }
    }
}
