package kreyj.sitzplatzreservierung;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(SzenarioTest.TestVerzeichnis.class)
class SzenarioTest {

    public static class TestVerzeichnis implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("zuteilung.szenarien.verzeichnis", "target/test-szenarien-" + System.nanoTime());
        }
    }

    private static String szenario(String name, String schritte) {
        return "{ \"name\": \"" + name + "\", \"beschreibung\": \"Test\", \"wagenId\": \"W1\", \"schritte\": [" + schritte + "] }";
    }

    private static final String ANFRAGE = "{ \"typ\": \"ANFRAGE\", \"bezeichnung\": \"Familie\", \"anfrage\": { \"gruppengroesse\": 3, \"mitKindern\": true } }";

    private static Response post(String body) {
        return given().contentType(ContentType.JSON).body(body).post("/api/v1/szenarien");
    }

    @Test
    void testAnlegenLadenAendernUndLoeschen() {
        Response angelegt = post(szenario("Größe Ünder-Fahrt", ANFRAGE + ", { \"typ\": \"STORNO\", \"bezugSchritt\": 1 }"));
        assertEquals(201, angelegt.statusCode());
        String id = angelegt.jsonPath().getString("id");
        assertEquals("groesse-uender-fahrt", id);

        // gleicher Name: Id wird nummeriert
        assertEquals("groesse-uender-fahrt-2", post(szenario("Größe Ünder-Fahrt", ANFRAGE)).jsonPath().getString("id"));

        Response geladen = given().get("/api/v1/szenarien/" + id);
        assertEquals(200, geladen.statusCode());
        assertEquals(2, geladen.jsonPath().getList("schritte").size());
        assertEquals(3, geladen.jsonPath().getInt("schritte[0].anfrage.gruppengroesse"));
        assertEquals("STORNO", geladen.jsonPath().getString("schritte[1].typ"));

        assertTrue(given().get("/api/v1/szenarien").jsonPath().getList("id").contains(id));

        Response geaendert = given().contentType(ContentType.JSON).body(szenario("Neuer Name", ANFRAGE)).put("/api/v1/szenarien/" + id);
        assertEquals(200, geaendert.statusCode());
        assertEquals(id, geaendert.jsonPath().getString("id"));
        assertEquals("Neuer Name", given().get("/api/v1/szenarien/" + id).jsonPath().getString("name"));

        assertEquals(204, given().delete("/api/v1/szenarien/" + id).statusCode());
        assertEquals(404, given().get("/api/v1/szenarien/" + id).statusCode());
        assertEquals(404, given().delete("/api/v1/szenarien/" + id).statusCode());
    }

    @Test
    void testUngueltigeSzenarienUndIds() {
        assertEquals(400, post(szenario("", ANFRAGE)).statusCode());
        assertEquals(400, post(szenario("Ohne Anfrage", "{ \"typ\": \"ANFRAGE\" }")).statusCode());
        assertEquals(400, post(szenario("Storno zuerst", "{ \"typ\": \"STORNO\", \"bezugSchritt\": 1 }")).statusCode());
        assertEquals(400, post(szenario("Storno auf Storno",
                ANFRAGE + ", { \"typ\": \"STORNO\", \"bezugSchritt\": 1 }, { \"typ\": \"STORNO\", \"bezugSchritt\": 2 }")).statusCode());

        // Pfadangriffe und ungültige Ids werden abgewiesen
        assertEquals(400, given().get("/api/v1/szenarien/..%2Fapplication").statusCode());
        assertEquals(400, given().get("/api/v1/szenarien/GROSS").statusCode());
        assertEquals(404, given().contentType(ContentType.JSON).body(szenario("X", ANFRAGE)).put("/api/v1/szenarien/gibt-es-nicht").statusCode());
    }
}
