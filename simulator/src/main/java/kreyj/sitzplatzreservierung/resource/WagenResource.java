package kreyj.sitzplatzreservierung.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.sitzplatzreservierung.dto.BestaetigungRequest;
import kreyj.sitzplatzreservierung.dto.GruppenreservierungRequest;
import kreyj.sitzplatzreservierung.dto.KennzahlenResponse;
import kreyj.sitzplatzreservierung.dto.ReservierungResponse;
import kreyj.sitzplatzreservierung.dto.SitzplanResponse;
import kreyj.sitzplatzreservierung.dto.VormerkRequest;
import kreyj.sitzplatzreservierung.dto.WagenAnlegenRequest;
import kreyj.sitzplatzreservierung.dto.WagenResponse;
import kreyj.sitzplatzreservierung.exception.UngueltigeAnfrageException;
import kreyj.sitzplatzreservierung.exception.WagenNichtGefundenException;
import kreyj.sitzplatzreservierung.layout.Sitzplanzeichner;
import kreyj.sitzplatzreservierung.layout.Sitzplanzeichner.PlatzBezug;
import kreyj.sitzplatzreservierung.layout.Wagenansicht;
import kreyj.sitzplatzreservierung.model.Sitzplatz;
import kreyj.sitzplatzreservierung.model.Wagen;
import kreyj.sitzplatzreservierung.service.SitzzuteilungService;
import kreyj.sitzplatzreservierung.service.WagenAnlage;
import kreyj.sitzplatzreservierung.service.WagenlayoutKatalog;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Path("/api/v1/wagen")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WagenResource {

    @Inject
    SitzzuteilungService zuteilungService;

    @Inject
    WagenAnlage wagenAnlage;

    @Inject
    WagenlayoutKatalog katalog;

    @GET
    public List<WagenResponse> auflisten() {
        List<Wagen> wagen = Wagen.list("order by id");
        return wagen.stream().map(WagenResponse::von).toList();
    }

    /**
     * Legt einen Wagen an; seine Sitzplätze werden aus dem gewählten Wagenlayout abgeleitet.
     */
    @POST
    public Response anlegen(@Valid WagenAnlegenRequest request) {
        return Response.status(Response.Status.CREATED).entity(WagenResponse.von(wagenAnlage.anlegen(request))).build();
    }

    /**
     * Gezeichnete Draufsicht des Wagens aus seinem Layout (Umriss, Zonen, Tische, Plätze, Piktogramme); die Formen der Plätze
     * tragen die Id des Sitzplatzes. Den Zustand der Plätze liefert der Sitzplan.
     */
    @GET
    @Path("/{wagenId}/ansicht")
    public Wagenansicht ansicht(@PathParam("wagenId") String wagenId) {
        Wagen wagen = Wagen.findById(wagenId);
        if (wagen == null) throw new WagenNichtGefundenException(wagenId);
        if (wagen.layout == null) throw new UngueltigeAnfrageException("Wagen " + wagenId + " hat kein Wagenlayout.");

        Map<String, PlatzBezug> plaetze = new HashMap<>();
        for (Sitzplatz sitzplatz : Sitzplatz.<Sitzplatz>list("wagenId = ?1 and elementId is not null", wagenId))
            plaetze.put(sitzplatz.elementId, new PlatzBezug(sitzplatz.id, sitzplatz.anzeigename(), sitzplatz.art.name().toLowerCase(Locale.ROOT)));
        return Sitzplanzeichner.zeichne(katalog.layout(wagen.layout), plaetze);
    }

    @GET
    @Path("/{wagenId}")
    public WagenResponse abrufen(@PathParam("wagenId") String wagenId) {
        Wagen wagen = Wagen.findById(wagenId);
        if (wagen == null) throw new WagenNichtGefundenException(wagenId);
        return WagenResponse.von(wagen);
    }

    /**
     * Sitzplan mit Verfügbarkeit je Sitzplatz für Teilstrecke und Gruppengröße, ohne Vorschlag.
     */
    @GET
    @Path("/{wagenId}/sitzplan")
    public SitzplanResponse sitzplan(
            @PathParam("wagenId") String wagenId,
            @QueryParam("einstiegHalt") Integer einstiegHalt,
            @QueryParam("ausstiegHalt") Integer ausstiegHalt,
            @QueryParam("gruppengroesse") @DefaultValue("1") int gruppengroesse,
            @QueryParam("wuenschtRuhezone") boolean wuenschtRuhezone,
            @QueryParam("wuenschtRollstuhlplatz") boolean wuenschtRollstuhlplatz,
            @QueryParam("wuenschtFahrradplatz") boolean wuenschtFahrradplatz,
            @QueryParam("mitKindern") boolean mitKindern,
            @QueryParam("vormerkToken") String vormerkToken,
            @QueryParam("anfragezeitpunkt") Instant anfragezeitpunkt
    ) {
        return zuteilungService.sitzplanErstellen(wagenId,
                new GruppenreservierungRequest(gruppengroesse, wuenschtRuhezone,
                        wuenschtRollstuhlplatz, wuenschtFahrradplatz, mitKindern, einstiegHalt, ausstiegHalt, vormerkToken, anfragezeitpunkt),
                false);
    }

    /**
     * Liefert den besten Vorschlag und den Sitzplan, bucht nicht, merkt die Sitzplätze aber vor.
     */
    @POST
    @Path("/{wagenId}/vorschlag")
    public SitzplanResponse vorschlag(@PathParam("wagenId") String wagenId, @Valid GruppenreservierungRequest request) {
        return zuteilungService.sitzplanErstellen(wagenId, request, true);
    }

    /**
     * Ersetzt die Vormerkung des Tokens durch die gewählten Sitzplätze (manuelles Umwählen).
     */
    @POST
    @Path("/{wagenId}/vormerkung")
    public SitzplanResponse vormerkung(@PathParam("wagenId") String wagenId, @Valid VormerkRequest request) {
        return zuteilungService.vormerken(wagenId, request);
    }

    @POST
    @Path("/{wagenId}/bestaetigung")
    public ReservierungResponse bestaetigung(@PathParam("wagenId") String wagenId, @Valid BestaetigungRequest request) {
        return zuteilungService.bestaetigen(wagenId, request);
    }

    @GET
    @Path("/{wagenId}/reservierungen")
    public List<ReservierungResponse> reservierungen(@PathParam("wagenId") String wagenId) {
        return zuteilungService.reservierungen(wagenId);
    }

    @DELETE
    @Path("/{wagenId}/reservierungen/{reservierungId}")
    public Response stornieren(@PathParam("wagenId") String wagenId, @PathParam("reservierungId") String reservierungId) {
        return Response.ok(Map.of("geloescht", zuteilungService.stornieren(wagenId, reservierungId))).build();
    }

    @GET
    @Path("/{wagenId}/kennzahlen")
    public KennzahlenResponse kennzahlen(@PathParam("wagenId") String wagenId) {
        return zuteilungService.kennzahlen(wagenId);
    }

    @DELETE
    @Path("/{wagenId}/reservierungen")
    public Response zuruecksetzen(@PathParam("wagenId") String wagenId) {
        return Response.ok(Map.of("geloescht", zuteilungService.zuruecksetzen(wagenId))).build();
    }
}
