package org.example.train.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.train.dto.Szenario;
import org.example.train.dto.SzenarioUebersicht;
import org.example.train.service.SzenarioSpeicher;

import java.util.List;

@Path("/api/v1/szenarien")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SzenarioResource {

    @Inject
    SzenarioSpeicher speicher;

    @GET
    public List<SzenarioUebersicht> auflisten() {
        return speicher.auflisten();
    }

    @GET
    @Path("/{id}")
    public Szenario abrufen(@PathParam("id") String id) {
        return speicher.laden(id);
    }

    @POST
    public Response anlegen(@Valid Szenario szenario) {
        return Response.status(Response.Status.CREATED).entity(speicher.anlegen(szenario)).build();
    }

    @PUT
    @Path("/{id}")
    public Szenario ersetzen(@PathParam("id") String id, @Valid Szenario szenario) {
        return speicher.ersetzen(id, szenario);
    }

    @DELETE
    @Path("/{id}")
    public Response loeschen(@PathParam("id") String id) {
        speicher.loeschen(id);
        return Response.noContent().build();
    }
}
