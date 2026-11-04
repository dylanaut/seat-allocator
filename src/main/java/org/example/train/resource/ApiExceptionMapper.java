package org.example.train.resource;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.train.exception.ReservierungNichtGefundenException;
import org.example.train.exception.ReservierungsKonfliktException;
import org.example.train.exception.SzenarioNichtGefundenException;
import org.example.train.exception.UngueltigeAnfrageException;
import org.example.train.exception.WagenNichtGefundenException;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Map;

public class ApiExceptionMapper {

    @ServerExceptionMapper
    public Response nichtGefunden(WagenNichtGefundenException e) {
        return fehler(Response.Status.NOT_FOUND, e);
    }

    @ServerExceptionMapper
    public Response reservierungNichtGefunden(ReservierungNichtGefundenException e) {
        return fehler(Response.Status.NOT_FOUND, e);
    }

    @ServerExceptionMapper
    public Response szenarioNichtGefunden(SzenarioNichtGefundenException e) {
        return fehler(Response.Status.NOT_FOUND, e);
    }

    @ServerExceptionMapper
    public Response ungueltigeAnfrage(UngueltigeAnfrageException e) {
        return fehler(Response.Status.BAD_REQUEST, e);
    }

    @ServerExceptionMapper
    public Response konflikt(ReservierungsKonfliktException e) {
        return fehler(Response.Status.CONFLICT, e);
    }

    private Response fehler(Response.Status status, RuntimeException e) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(Map.of("fehler", e.getMessage())).build();
    }
}
