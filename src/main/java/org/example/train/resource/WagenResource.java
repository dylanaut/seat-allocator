package org.example.train.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.train.dto.BestaetigungRequest;
import org.example.train.dto.GruppenreservierungRequest;
import org.example.train.dto.KennzahlenResponse;
import org.example.train.dto.ReservierungResponse;
import org.example.train.dto.SitzplanResponse;
import org.example.train.dto.VormerkRequest;
import org.example.train.dto.WagenResponse;
import org.example.train.exception.WagenNichtGefundenException;
import org.example.train.model.Wagen;
import org.example.train.service.SitzzuteilungService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Path("/api/v1/wagen")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WagenResource {

    @Inject
    SitzzuteilungService zuteilungService;

    @GET
    public List<WagenResponse> auflisten() {
        List<Wagen> wagen = Wagen.list("order by id");
        return wagen.stream().map(WagenResponse::von).toList();
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
                new GruppenreservierungRequest(gruppengroesse, false, false, false, wuenschtRuhezone,
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
