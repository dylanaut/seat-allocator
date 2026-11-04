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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ZonenUndSonderplaetzeTest {

    private static void wagen(String id) {
        Wagen w = new Wagen();
        w.id = id;
        w.zugnummer = "TEST";
        w.persist();
    }

    private static void platz(String wagenId, Platzart art, int reihe, int spalte, boolean tisch, Zone zone) {
        Sitzplatz s = new Sitzplatz();
        s.id = wagenId + "-" + reihe + (char) ('A' + spalte);
        s.wagenId = wagenId;
        s.art = art;
        s.reihe = reihe;
        s.spalte = spalte;
        s.hatTisch = tisch;
        s.zone = zone;
        s.persist();
    }

    private static void reihe(String wagenId, int reihe, boolean tisch, Zone zone) {
        for (int c = 0; c < 4; c++)
            platz(wagenId, Platzart.SITZ, reihe, c, tisch, zone);
    }

    private static String gruppe(int n, String extra) {
        return "{ \"gruppengroesse\": " + n + (extra.isEmpty() ? "" : ", " + extra) + " }";
    }

    private static Response post(String wagenId, String pfad, String body) {
        return given().contentType(ContentType.JSON).body(body).post("/api/v1/wagen/" + wagenId + "/" + pfad);
    }

    @Test
    void testRollstuhlplatzWirdNurAufWunschVergeben() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("Z1");
            platz("Z1", Platzart.ROLLSTUHL, 0, 0, false, Zone.STANDARD);
            platz("Z1", Platzart.SITZ, 1, 0, false, Zone.STANDARD);
        });

        // ohne Wunsch bleibt der Rollstuhlplatz frei; der Sitzplatz wird vergeben
        Response normal = post("Z1", "sitzplaetze/zuteilen", "{}");
        assertEquals(200, normal.statusCode());
        assertEquals("Z1-1A", normal.jsonPath().getString("id"));
        assertEquals(409, post("Z1", "sitzplaetze/zuteilen", "{}").statusCode());

        // mit Wunsch gibt es genau den Rollstuhlplatz, danach ist er auf der Strecke belegt
        Response rollstuhl = post("Z1", "vorschlag", gruppe(1, "\"wuenschtRollstuhlplatz\": true"));
        assertEquals(List.of("Z1-0A"), rollstuhl.jsonPath().getList("vorgeschlagen"));
        String token = rollstuhl.jsonPath().getString("vormerkToken");
        assertEquals(200, post("Z1", "bestaetigung",
                "{ \"sitzIds\": [\"Z1-0A\"], \"vormerkToken\": \"" + token + "\" }").statusCode());

        Response nochmal = post("Z1", "vorschlag", gruppe(1, "\"wuenschtRollstuhlplatz\": true"));
        assertEquals(0, nochmal.jsonPath().getList("vorgeschlagen").size());
        assertEquals("Kein Rollstuhlplatz auf dieser Teilstrecke verfügbar.", nochmal.jsonPath().getString("meldung"));
    }

    @Test
    void testJeWagenGibtEsZweiRollstuhlplaetzeAnDenEnden() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("Z7");
            platz("Z7", Platzart.ROLLSTUHL, 0, 1, false, Zone.STANDARD);
            platz("Z7", Platzart.ROLLSTUHL, 11, 1, false, Zone.STANDARD);
        });

        Set<String> vergeben = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            Response r = post("Z7", "sitzplaetze/gruppe-zuteilen", gruppe(1, "\"wuenschtRollstuhlplatz\": true"));
            assertEquals(200, r.statusCode());
            vergeben.add(r.jsonPath().getString("[0].id"));
        }
        assertEquals(Set.of("Z7-0B", "Z7-11B"), vergeben);
        assertEquals(409, post("Z7", "sitzplaetze/gruppe-zuteilen", gruppe(1, "\"wuenschtRollstuhlplatz\": true")).statusCode());
    }

    @Test
    void testRollstuhlersetztSitzplatzUndFahrradKommtHinzu() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("Z2");
            platz("Z2", Platzart.ROLLSTUHL, 0, 0, false, Zone.STANDARD);
            platz("Z2", Platzart.FAHRRAD, 9, 0, false, Zone.STANDARD);
            reihe("Z2", 1, false, Zone.STANDARD);
        });

        // 3 Personen mit Rollstuhl: Rollstuhlplatz + 2 Sitzplätze
        Response r = post("Z2", "sitzplaetze/gruppe-zuteilen", gruppe(3, "\"wuenschtRollstuhlplatz\": true"));
        assertEquals(200, r.statusCode());
        List<String> arten = r.jsonPath().getList("art");
        assertEquals(1, arten.stream().filter("ROLLSTUHL"::equals).count());
        assertEquals(2, arten.stream().filter("SITZ"::equals).count());

        // 2 Personen mit Fahrrad: 2 Sitzplätze + Fahrradstellplatz, gezählt werden 2 Personen
        Response f = post("Z2", "sitzplaetze/gruppe-zuteilen", gruppe(1, "\"wuenschtFahrradplatz\": true"));
        assertEquals(200, f.statusCode());
        assertEquals(2, f.jsonPath().getList("id").size());
        assertTrue(f.jsonPath().getList("art").contains("FAHRRAD"));

        List<Integer> personen = given().get("/api/v1/wagen/Z2/reservierungen").jsonPath().getList("gruppengroesse");
        assertEquals(List.of(3, 1), personen);
    }

    @Test
    void testFamilienBekommenKinderbereichAndereNurAlsFallback() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("Z3");
            reihe("Z3", 1, false, Zone.STANDARD);
            reihe("Z3", 2, false, Zone.KINDER);
        });

        Response familie = post("Z3", "sitzplaetze/gruppe-zuteilen", gruppe(3, "\"mitKindern\": true"));
        assertEquals(200, familie.statusCode());
        assertEquals(Set.of("KINDER"), new HashSet<String>(familie.jsonPath().getList("zone")));

        Response andere = post("Z3", "sitzplaetze/gruppe-zuteilen", gruppe(3, ""));
        assertEquals(200, andere.statusCode());
        assertEquals(Set.of("STANDARD"), new HashSet<String>(andere.jsonPath().getList("zone")));

        // Rest: ein Kinderplatz und ein Standardplatz; Einzelreisender nimmt zuerst Standard, dann den Kinderbereich
        assertEquals("STANDARD", post("Z3", "sitzplaetze/zuteilen", "{}").jsonPath().getString("zone"));
        assertEquals("KINDER", post("Z3", "sitzplaetze/zuteilen", "{}").jsonPath().getString("zone"));
    }

    @Test
    void testFamilienKommenNieInDieRuhezone() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("Z4");
            reihe("Z4", 1, false, Zone.RUHE);
        });

        assertEquals(409, post("Z4", "sitzplaetze/gruppe-zuteilen", gruppe(2, "\"mitKindern\": true")).statusCode());
        assertEquals(200, post("Z4", "sitzplaetze/gruppe-zuteilen", gruppe(2, "")).statusCode());
    }

    @Test
    void testRuhezoneUndKinderbereichSchliessenSichAus() {
        QuarkusTransaction.requiringNew().run(() -> wagen("Z5"));
        assertEquals(400, post("Z5", "vorschlag", gruppe(2, "\"wuenschtRuhezone\": true, \"mitKindern\": true")).statusCode());
    }

    @Test
    void testGruppeMitTischwunschBekommtGanzenTisch() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("Z6");
            for (int r = 4; r <= 7; r++)
                reihe("Z6", r, true, Zone.STANDARD);
        });

        Response r = post("Z6", "sitzplaetze/gruppe-zuteilen", gruppe(4, "\"wuenschtTisch\": true"));
        assertEquals(200, r.statusCode());

        List<Integer> reihen = r.jsonPath().getList("reihe");
        List<Integer> spalten = r.jsonPath().getList("spalte");
        // Ein Tisch: zwei gegenüberliegende Reihen (Paar 4/5 oder 6/7) auf derselben Gangseite
        assertEquals(1, reihen.stream().map(x -> x / 2).distinct().count());
        assertEquals(2, reihen.stream().distinct().count());
        assertEquals(1, spalten.stream().map(x -> x / 2).distinct().count());
        assertEquals(2, spalten.stream().distinct().count());
    }
}
