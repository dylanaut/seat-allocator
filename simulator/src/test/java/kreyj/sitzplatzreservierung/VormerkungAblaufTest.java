package kreyj.sitzplatzreservierung;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(VormerkungAblaufTest.EineSekundeVormerkung.class)
class VormerkungAblaufTest {

    public static class EineSekundeVormerkung implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("zuteilung.vormerkung.sekunden", "1");
        }
    }

    @Test
    void testHoldExpiresAndSeatBecomesAvailableAgain() throws InterruptedException {
        VormerkungTest.wagon("H3", 1);

        Response vorgemerkt = VormerkungTest.post("H3/vorschlag", VormerkungTest.propose());
        String sitzplatz = vorgemerkt.jsonPath().getString("vorgeschlagen[0]");
        assertEquals("VORGEMERKT", given().get("/api/v1/wagen/H3/sitzplan").jsonPath().getString("sitzplaetze[0].zustand"));
        assertEquals(409, VormerkungTest.post("H3/bestaetigung", VormerkungTest.confirm(null, sitzplatz)).statusCode());

        Thread.sleep(1500);

        assertEquals("FREI", given().get("/api/v1/wagen/H3/sitzplan").jsonPath().getString("sitzplaetze[0].zustand"));
        assertEquals(200, VormerkungTest.post("H3/bestaetigung", VormerkungTest.confirm(null, sitzplatz)).statusCode());
    }
}
