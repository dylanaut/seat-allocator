package org.example.train;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.example.train.model.Platzart;
import org.example.train.model.Sitzplatz;
import org.example.train.model.Wagen;
import org.example.train.model.Zone;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class StornoUndKennzahlenTest {

    private static void wagen(String id, String halte, int sitze) {
        QuarkusTransaction.requiringNew().run(() -> {
            Wagen w = new Wagen();
            w.id = id;
            w.zugnummer = "TEST";
            w.halte = halte;
            w.persist();
            for (int i = 0; i < sitze; i++) {
                Sitzplatz s = new Sitzplatz();
                s.id = id + "-" + i;
                s.wagenId = id;
                s.art = Platzart.SITZ;
                s.reihe = 1 + i / 4;
                s.spalte = i % 4;
                s.zone = Zone.STANDARD;
                s.persist();
            }
        });
    }

    private static Response bestaetigen(String wagenId, String body) {
        return given().contentType(ContentType.JSON).body(body).post("/api/v1/wagen/" + wagenId + "/bestaetigung");
    }

    @Test
    void testStornoGibtSitzplaetzeFrei() {
        wagen("S1", "A,B", 2);

        String id = bestaetigen("S1", "{ \"sitzIds\": [\"S1-0\", \"S1-1\"] }").jsonPath().getString("reservierungId");
        assertEquals(409, bestaetigen("S1", "{ \"sitzIds\": [\"S1-0\"] }").statusCode());

        assertEquals(200, given().delete("/api/v1/wagen/S1/reservierungen/" + id).statusCode());
        assertEquals(200, bestaetigen("S1", "{ \"sitzIds\": [\"S1-0\"] }").statusCode());

        assertEquals(404, given().delete("/api/v1/wagen/S1/reservierungen/" + id).statusCode());
        assertEquals(404, given().delete("/api/v1/wagen/NOPE/reservierungen/" + id).statusCode());
    }

    @Test
    void testKennzahlen() {
        wagen("S2", "A,B,C", 4);

        Response leer = given().get("/api/v1/wagen/S2/kennzahlen");
        assertEquals(0.0f, leer.jsonPath().getFloat("auslastungGesamt"));
        assertEquals(2, leer.jsonPath().getList("auslastungJeTeilstrecke").size());

        // Gruppe mit 2 Sitzen A-B (zusammenhängend) auf der ganzen Strecke, 1 Sitz nur auf A-B
        bestaetigen("S2", "{ \"sitzIds\": [\"S2-0\", \"S2-1\"] }");
        bestaetigen("S2", "{ \"sitzIds\": [\"S2-2\"], \"einstiegHalt\": 0, \"ausstiegHalt\": 1 }");

        Response k = given().get("/api/v1/wagen/S2/kennzahlen");
        assertEquals(75.0f, k.jsonPath().getFloat("auslastungJeTeilstrecke[0]"));
        assertEquals(50.0f, k.jsonPath().getFloat("auslastungJeTeilstrecke[1]"));
        assertEquals(62.5f, k.jsonPath().getFloat("auslastungGesamt"));
        assertEquals(2, k.jsonPath().getInt("reservierungen"));
        assertEquals(1, k.jsonPath().getInt("gruppen"));
        assertEquals(1, k.jsonPath().getInt("gruppenZusammenhaengend"));
    }
}
