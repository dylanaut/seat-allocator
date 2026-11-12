package kreyj.sitzplatzreservierung.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.sitzplatzreservierung.dto.LayoutUebersicht;
import kreyj.sitzplatzreservierung.layout.Piktogrammart;
import kreyj.sitzplatzreservierung.service.WagenlayoutKatalog;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die verfügbaren Wagenlayouts und die Piktogramme, die in den Ansichten der Wagen referenziert werden.
 */
@Path("/api/v1/wagenlayouts")
@Produces(MediaType.APPLICATION_JSON)
public class WagenlayoutResource {

    @Inject
    WagenlayoutKatalog katalog;

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
}
