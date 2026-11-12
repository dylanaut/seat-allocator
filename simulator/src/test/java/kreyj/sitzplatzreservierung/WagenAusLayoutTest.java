package kreyj.sitzplatzreservierung;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WagenAusLayoutTest {

    private static Map<String, Object> neu(String id, String layout, boolean kontingent) {
        return Map.of("id", id, "layout", layout, "halte", List.of("A", "B", "C"), "gruppenkontingent", kontingent);
    }

    @Test
    void testBeimStartEntstehtJeLayoutEinWagen() {
        given().get("/api/v1/wagen").then().statusCode(200)
                .body("id", hasItems("Beispiel-Klein", "Beispiel-Mittel", "Beispiel-Gross"))
                .body("find { it.id == 'Beispiel-Klein' }.layout", is("Beispiel-Klein"))
                .body("find { it.id == 'Beispiel-Klein' }.halte", hasSize(4));
        given().get("/api/v1/wagen/Beispiel-Klein/sitzplan").then().statusCode(200).body("sitzplaetze", hasSize(27))
                .body("sitzplaetze.find { it.id == 'Beispiel-Klein-RS1' }.art", is("ROLLSTUHL"))
                .body("sitzplaetze.find { it.id == 'Beispiel-Klein-3' }.bezeichnung", is("3"));
    }

    @Test
    void testAnsichtDesWagensAusSeinemLayout() {
        given().get("/api/v1/wagen/Beispiel-Mittel/ansicht").then().statusCode(200)
                .body("breiteM", greaterThan(20f))
                .body("formen.findAll { it.stile.contains('platz') }.size()", is(50))
                .body("formen.find { it.id == 'Beispiel-Mittel-1' }.details.Platz", is("1"))
                .body("formen.find { it.id == 'Beispiel-Mittel-FR1' }.piktogramm.art", is("FAHRRAD"))
                .body("formen.stile.flatten()", not(hasItems("schicht-tuer", "schicht-flaeche")));
        given().get("/api/v1/wagen/gibt-es-nicht/ansicht").then().statusCode(404);
    }

    @Test
    void testWagenAnlegenUndReservieren() {
        given().contentType(ContentType.JSON).body(neu("T-neu", "Beispiel-Klein", false)).post("/api/v1/wagen").then().statusCode(201)
                .body("id", is("T-neu")).body("layout", is("Beispiel-Klein")).body("halte", hasSize(3));

        /*  Ohne Gruppenkontingent ist kein Platz für Gruppen reserviert  */
        var sitzplan = given().get("/api/v1/wagen/T-neu/sitzplan").then().statusCode(200).body("sitzplaetze", hasSize(27)).extract().path("sitzplaetze.zustand");
        assertTrue(((List<?>) sitzplan).stream().noneMatch("GRUPPENKONTINGENT"::equals));

        var vorschlag = given().contentType(ContentType.JSON).body("{ \"gruppengroesse\": 4 }").post("/api/v1/wagen/T-neu/vorschlag").then().statusCode(200).extract();
        List<String> ids = vorschlag.path("vorgeschlagen");
        assertEquals(4, ids.size());
        assertTrue(ids.stream().allMatch((id) -> id.startsWith("T-neu-")));
        given().contentType(ContentType.JSON)
                .body(Map.of("sitzIds", ids, "vormerkToken", vorschlag.<String>path("vormerkToken"), "einstiegHalt", 0, "ausstiegHalt", 2))
                .post("/api/v1/wagen/T-neu/bestaetigung").then().statusCode(200).body("sitzIds", hasSize(4));
        given().get("/api/v1/wagen/T-neu/sitzplan?einstiegHalt=0&ausstiegHalt=2").then().body("sitzplaetze.findAll { it.zustand == 'BELEGT' }", hasSize(4));
    }

    @Test
    void testGruppenkontingentAusDerTischgruppe() {
        given().contentType(ContentType.JSON).body(neu("T-kontingent", "Beispiel-Klein", true)).post("/api/v1/wagen").then().statusCode(201);

        given().get("/api/v1/wagen/T-kontingent/sitzplan").then().statusCode(200)
                .body("sitzplaetze.findAll { it.zustand == 'GRUPPENKONTINGENT' }", hasSize(4));
    }

    @Test
    void testUngueltigeAnfragen() {
        given().contentType(ContentType.JSON).body(neu("Beispiel-Klein", "Beispiel-Klein", false)).post("/api/v1/wagen").then().statusCode(400)
                .body("fehler", containsString("existiert bereits"));
        given().contentType(ContentType.JSON).body(neu("T-x", "gibt-es-nicht", false)).post("/api/v1/wagen").then().statusCode(404);
        given().contentType(ContentType.JSON).body(neu("../T-x", "Beispiel-Klein", false)).post("/api/v1/wagen").then().statusCode(400);
        given().contentType(ContentType.JSON).body("{ \"id\": \"T-y\" }").post("/api/v1/wagen").then().statusCode(400);
    }

    @Test
    void testLayoutUebersichtUndPiktogramme() {
        given().get("/api/v1/wagenlayouts").then().statusCode(200).body("kennung", hasItems("Beispiel-Klein", "Beispiel-Mittel", "Beispiel-Gross"))
                .body("find { it.kennung == 'Beispiel-Klein' }.sitzplaetze", is(27));
        given().get("/api/v1/wagenlayouts/piktogramme").then().statusCode(200).body("ROLLSTUHL", containsString("<circle"));
    }
}
