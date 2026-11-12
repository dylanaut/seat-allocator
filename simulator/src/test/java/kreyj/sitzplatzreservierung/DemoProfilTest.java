package kreyj.sitzplatzreservierung;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft das Profil "demo": gleiche Anwendung auf der eingebetteten H2-Datenbank mit den Wagen, die beim Start aus den Wagenlayouts angelegt werden.
 */
@QuarkusTest
@TestProfile(DemoProfilTest.Demo.class)
class DemoProfilTest {

    public static class Demo implements QuarkusTestProfile {

        @Override
        public String getConfigProfile() {
            return "demo";
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("zuteilung.szenarien.verzeichnis", "target/test-szenarien-demo-" + System.nanoTime());
        }
    }

    @Test
    void testTestdatenUndReservierungsablaufAufH2() {
        Response wagen = given().get("/api/v1/wagen");
        assertEquals(200, wagen.statusCode());
        assertEquals("München", wagen.jsonPath().getString("[0].halte[3]"));
        assertTrue(wagen.jsonPath().getList("id").contains("Beispiel-Mittel"));

        String body = "{ \"gruppengroesse\": 3, \"einstiegHalt\": 0, \"ausstiegHalt\": 3 }";
        Response vorschlag = given().contentType(ContentType.JSON).body(body).post("/api/v1/wagen/Beispiel-Mittel/vorschlag");
        assertEquals(200, vorschlag.statusCode());
        List<String> ids = vorschlag.jsonPath().getList("vorgeschlagen");
        assertEquals(3, ids.size());
        assertEquals(50, vorschlag.jsonPath().getList("sitzplaetze").size());

        Response bestaetigt = given().contentType(ContentType.JSON)
                .body("{ \"sitzIds\": " + ids.stream().map(i -> "\"" + i + "\"").toList()
                        + ", \"vormerkToken\": \"" + vorschlag.jsonPath().getString("vormerkToken")
                        + "\", \"einstiegHalt\": 0, \"ausstiegHalt\": 3 }")
                .post("/api/v1/wagen/Beispiel-Mittel/bestaetigung");
        assertEquals(200, bestaetigt.statusCode());

        assertTrue(given().get("/api/v1/wagen/Beispiel-Mittel/kennzahlen").jsonPath().getFloat("auslastungGesamt") > 0);
        assertEquals(200, given().delete("/api/v1/wagen/Beispiel-Mittel/reservierungen").statusCode());
    }

    @Test
    void testSzenarioDateienUndOberflaeche() {
        assertEquals(200, given().get("/api/v1/szenarien").statusCode());
        assertEquals(200, given().get("/").statusCode());
        assertEquals(200, given().get("/szenarien.html").statusCode());
    }
}
