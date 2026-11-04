package org.example.train;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.example.train.model.Wagen;
import org.example.train.model.Sitzplatz;
import org.example.train.model.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ReservierungsablaufTest {

    private static void wagon(String id, String halte, int sitzplaetze) {
        QuarkusTransaction.requiringNew().run(() -> {
            Wagen c = new Wagen();
            c.id = id;
            c.zugnummer = "TEST";
            c.halte = halte;
            c.persist();
            for (int i = 0; i < sitzplaetze; i++) {
                Sitzplatz s = new Sitzplatz();
                s.id = id + "-" + (char) ('A' + i);
                s.wagenId = id;
                s.reihe = 1;
                s.spalte = i;
                s.zone = Zone.STANDARD;
                s.persist();
            }
        });
    }

    private static Response post(String path, String body) {
        return given().contentType(ContentType.JSON).body(body).post("/api/v1/wagen/" + path);
    }

    private static String propose(int party, int from, int to) {
        return propose(party, from, to, null);
    }

    private static String propose(int party, int from, int to, String token) {
        return """
            { "gruppengroesse": %d, "wuenschtSteckdose": false, "wuenschtFenster": false, "wuenschtTisch": false, "wuenschtRuhezone": false,
              "einstiegHalt": %d, "ausstiegHalt": %d, "vormerkToken": %s }
            """.formatted(party, from, to, token == null ? "null" : "\"" + token + "\"");
    }

    private static String confirm(List<String> ids, int from, int to) {
        return confirm(ids, from, to, null);
    }

    private static String confirm(List<String> ids, int from, int to, String token) {
        String list = String.join("\",\"", ids);
        return "{ \"sitzIds\": [\"" + list + "\"], \"einstiegHalt\": " + from + ", \"ausstiegHalt\": " + to
                + ", \"vormerkToken\": " + (token == null ? "null" : "\"" + token + "\"") + " }";
    }

    @Test
    void testProposeDoesNotBookAndConfirmDoes() {
        wagon("F1", "A,B,C", 2);

        Response p = post("F1/vorschlag", propose(2, 0, 2));
        assertEquals(200, p.statusCode());
        List<String> vorgeschlagen = p.jsonPath().getList("vorgeschlagen");
        String token = p.jsonPath().getString("vormerkToken");
        assertEquals(2, vorgeschlagen.size());

        // Vorschlag bucht nicht: mit demselben Token kommt derselbe Vorschlag erneut
        assertEquals(vorgeschlagen, post("F1/vorschlag", propose(2, 0, 2, token)).jsonPath().getList("vorgeschlagen"));

        assertEquals(200, post("F1/bestaetigung", confirm(vorgeschlagen, 0, 2, token)).statusCode());

        // Danach sind die Sitze vergeben: kein Vorschlag mehr, Bestätigung ergibt 409
        Response again = post("F1/vorschlag", propose(2, 0, 2));
        assertEquals(0, again.jsonPath().getList("vorgeschlagen").size());
        assertEquals("BELEGT", again.jsonPath().getString("sitzplaetze[0].zustand"));
        assertEquals(409, post("F1/bestaetigung", confirm(vorgeschlagen, 0, 2)).statusCode());

        assertEquals(1, given().get("/api/v1/wagen/F1/reservierungen").jsonPath().getList("$").size());
    }

    @Test
    void testSeatCanBeReusedOnNonOverlappingSegments() {
        wagon("F2", "A,B,C,D", 1);

        assertEquals(200, post("F2/bestaetigung", confirm(List.of("F2-A"), 0, 1)).statusCode());
        // überlappend: Sitz belegt
        assertEquals(409, post("F2/bestaetigung", confirm(List.of("F2-A"), 0, 2)).statusCode());
        assertEquals(409, post("F2/bestaetigung", confirm(List.of("F2-A"), 0, 1)).statusCode());
        // anschließend: derselbe Sitz ist frei
        assertEquals(200, post("F2/bestaetigung", confirm(List.of("F2-A"), 1, 3)).statusCode());
    }

    @Test
    void testPlanReflectsSegment() {
        wagon("F3", "A,B,C", 1);
        assertEquals(200, post("F3/bestaetigung", confirm(List.of("F3-A"), 0, 1)).statusCode());

        assertEquals("BELEGT", given().get("/api/v1/wagen/F3/sitzplan?einstiegHalt=0&ausstiegHalt=1").jsonPath().getString("sitzplaetze[0].zustand"));
        assertEquals("FREI", given().get("/api/v1/wagen/F3/sitzplan?einstiegHalt=1&ausstiegHalt=2").jsonPath().getString("sitzplaetze[0].zustand"));
    }

    @Test
    void testResetFreesSeats() {
        wagon("F4", null, 1);
        assertEquals(200, post("F4/bestaetigung", confirm(List.of("F4-A"), 0, 1)).statusCode());
        assertEquals(0, post("F4/vorschlag", propose(1, 0, 1)).jsonPath().getList("vorgeschlagen").size());

        assertEquals(200, given().delete("/api/v1/wagen/F4/reservierungen").statusCode());
        assertEquals(1, post("F4/vorschlag", propose(1, 0, 1)).jsonPath().getList("vorgeschlagen").size());
    }

    @Test
    void testInvalidSegmentAndUnknownCarriage() {
        wagon("F5", "A,B", 1);
        assertEquals(400, post("F5/vorschlag", propose(1, 1, 0)).statusCode());
        assertEquals(400, post("F5/vorschlag", propose(1, 0, 5)).statusCode());
        assertEquals(400, post("F5/bestaetigung", confirm(List.of("F5-A", "F5-A"), 0, 1)).statusCode());
        assertEquals(400, post("F5/bestaetigung", confirm(List.of("NOPE"), 0, 1)).statusCode());
        assertEquals(404, post("NOPE/vorschlag", propose(1, 0, 1)).statusCode());
        assertTrue(given().get("/api/v1/wagen").jsonPath().getList("id").contains("F5"));
    }
}
