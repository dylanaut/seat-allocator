package kreyj.sitzplatzreservierung.resource;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.sitzplatzreservierung.exception.ReservierungNichtGefundenException;
import kreyj.sitzplatzreservierung.exception.ReservierungsKonfliktException;
import kreyj.sitzplatzreservierung.exception.SzenarioNichtGefundenException;
import kreyj.sitzplatzreservierung.exception.UngueltigeAnfrageException;
import kreyj.sitzplatzreservierung.exception.WagenNichtGefundenException;
import kreyj.sitzplatzreservierung.exception.WagenlayoutNichtGefundenException;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Map;

public class ApiExceptionMapper {

    @ServerExceptionMapper
    public Response nichtGefunden(WagenNichtGefundenException e) {
        return fehler(Response.Status.NOT_FOUND, e);
    }

    @ServerExceptionMapper
    public Response layoutNichtGefunden(WagenlayoutNichtGefundenException e) {
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
