package org.example.train;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.example.train.model.Wagen;
import org.example.train.model.Sitzplatz;
import org.example.train.model.Zone;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class VormerkungTest {

    static void wagon(String id, int sitzplaetze) {
        QuarkusTransaction.requiringNew().run(() -> {
            Wagen c = new Wagen();
            c.id = id;
            c.zugnummer = "TEST";
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

    static Response post(String path, String body) {
        return given().contentType(ContentType.JSON).body(body).post("/api/v1/wagen/" + path);
    }

    static String propose() {
        return """
            { "gruppengroesse": 1, "wuenschtSteckdose": false, "wuenschtFenster": false, "wuenschtTisch": false, "wuenschtRuhezone": false }
            """;
    }

    static String hold(String token, String... ids) {
        String list = ids.length == 0 ? "" : "\"" + String.join("\",\"", ids) + "\"";
        return "{ \"vormerkToken\": " + (token == null ? "null" : "\"" + token + "\"") + ", \"sitzIds\": [" + list + "] }";
    }

    static String confirm(String token, String... ids) {
        return "{ \"vormerkToken\": " + (token == null ? "null" : "\"" + token + "\"")
                + ", \"sitzIds\": [\"" + String.join("\",\"", ids) + "\"] }";
    }

    @Test
    void testProposalsHoldSeatsAgainstOtherRequests() {
        wagon("H1", 2);

        Response a = post("H1/vorschlag", propose());
        Response b = post("H1/vorschlag", propose());
        String seatA = a.jsonPath().getString("vorgeschlagen[0]");
        String seatB = b.jsonPath().getString("vorgeschlagen[0]");
        assertNotNull(a.jsonPath().getString("vormerkToken"));
        assertNotNull(a.jsonPath().getString("vormerkungAblauf"));
        assertNotEquals(seatA, seatB);

        // Beide Sitze gehalten: keine weitere Anfrage bekommt einen Vorschlag, auch kein direktes Allocate
        assertEquals(0, post("H1/vorschlag", propose()).jsonPath().getList("vorgeschlagen").size());
        assertEquals(409, post("H1/sitzplaetze/zuteilen",
                "{ \"wuenschtSteckdose\": false, \"wuenschtFenster\": false, \"wuenschtTisch\": false, \"wuenschtRuhezone\": false }").statusCode());

        // Fremde sehen beide als VORGEMERKT, der Halter sieht den eigenen Sitz als FREI
        assertEquals("VORGEMERKT", given().get("/api/v1/wagen/H1/sitzplan").jsonPath().getString("sitzplaetze[0].zustand"));
        Response own = given().get("/api/v1/wagen/H1/sitzplan?vormerkToken=" + a.jsonPath().getString("vormerkToken"));
        assertEquals("FREI", own.jsonPath().getString("sitzplaetze.find { it.id == '" + seatA + "' }.zustand"));
        assertEquals("VORGEMERKT", own.jsonPath().getString("sitzplaetze.find { it.id == '" + seatB + "' }.zustand"));

        // Bestätigung eines fremd gehaltenen Sitzes scheitert, eigene gelingt und räumt die Sperre ab
        assertEquals(409, post("H1/bestaetigung", confirm(a.jsonPath().getString("vormerkToken"), seatB)).statusCode());
        assertEquals(200, post("H1/bestaetigung", confirm(a.jsonPath().getString("vormerkToken"), seatA)).statusCode());
        assertEquals("BELEGT", given().get("/api/v1/wagen/H1/sitzplan").jsonPath().getString("sitzplaetze.find { it.id == '" + seatA + "' }.zustand"));
    }

    @Test
    void testManualHoldReplacesAndReleases() {
        wagon("H2", 2);

        Response first = post("H2/vormerkung", hold(null, "H2-A"));
        assertEquals(200, first.statusCode());
        String token = first.jsonPath().getString("vormerkToken");
        assertNotNull(token);
        assertEquals("VORGEMERKT", given().get("/api/v1/wagen/H2/sitzplan").jsonPath().getString("sitzplaetze[0].zustand"));

        // Umwählen: A wird frei, B gehalten
        assertEquals(200, post("H2/vormerkung", hold(token, "H2-B")).statusCode());
        Response plan = given().get("/api/v1/wagen/H2/sitzplan");
        assertEquals("FREI", plan.jsonPath().getString("sitzplaetze[0].zustand"));
        assertEquals("VORGEMERKT", plan.jsonPath().getString("sitzplaetze[1].zustand"));

        // Fremder Halter kann B nicht übernehmen
        assertEquals(409, post("H2/vormerkung", hold(null, "H2-B")).statusCode());

        // Freigabe mit leerer Liste
        assertEquals(200, post("H2/vormerkung", hold(token)).statusCode());
        assertEquals("FREI", given().get("/api/v1/wagen/H2/sitzplan").jsonPath().getString("sitzplaetze[1].zustand"));
    }
}
