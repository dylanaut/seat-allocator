package org.example.train.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.train.dto.EinzelreservierungRequest;
import org.example.train.dto.GruppenreservierungRequest;
import org.example.train.dto.SitzplatzResponse;
import org.example.train.model.Sitzplatz;
import org.example.train.service.SitzzuteilungService;

import java.util.List;
import java.util.Map;

@Path("/api/v1/wagen/{wagenId}/sitzplaetze")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SitzplatzResource {

    @Inject
    SitzzuteilungService zuteilungService;

    @POST
    @Path("/zuteilen")
    public Response einzelplatzZuteilen(@PathParam("wagenId") String wagenId, @Valid EinzelreservierungRequest request) {
        return zuteilungService.einzelplatzZuteilen(wagenId, request)
                .map(sitzplatz -> Response.ok(SitzplatzResponse.von(sitzplatz)).build())
                .orElseGet(() -> Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("fehler", "Kein passender Sitzplatz verfügbar."))
                        .build());
    }

    @POST
    @Path("/gruppe-zuteilen")
    public Response gruppeZuteilen(@PathParam("wagenId") String wagenId, @Valid GruppenreservierungRequest request) {
        List<Sitzplatz> sitzplaetze = zuteilungService.gruppeZuteilen(wagenId, request);
        if (sitzplaetze.isEmpty()) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("fehler", "Keine zusammenhängende Sitzplatzgruppe verfügbar."))
                .build();
        }

        return Response.ok(sitzplaetze.stream().map(SitzplatzResponse::von).toList()).build();
    }
}
