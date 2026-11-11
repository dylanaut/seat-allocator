package kreyj.sitzplatzreservierung;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(DemoProfilTest.Demo.class)
class WagenlayoutResourceTest {

    private static final String BASIS = "/api/v1/wagenlayouts";
    private static final String LAYOUT = "Beispiel-Klein";

    private static String zugfahrt() {
        return "test-" + System.nanoTime();
    }

    private static Map<String, Object> belegung(int sitz, int von, int nach) {
        return Map.of("sitzNummer", sitz, "vonHalt", von, "nachHalt", nach);
    }

    private static List<String> stile(String zugfahrt, int sitz, int von, int nach) {
        return given().queryParam("zugfahrt", zugfahrt).queryParam("von", von).queryParam("nach", nach)
                .get(BASIS + "/" + LAYOUT + "/ansicht")
                .then().statusCode(200).extract().path("formen.find { it.details?.Platz == '" + sitz + "' && it.stile.contains('sitz') }.stile");
    }

    @Test
    void testUebersichtPiktogrammeUndHalte() {
        given().get(BASIS).then().statusCode(200)
                .body("kennung", hasItem(LAYOUT))
                .body("find { it.kennung == '" + LAYOUT + "' }.sitzplaetze", org.hamcrest.Matchers.equalTo(27));
        given().get(BASIS + "/piktogramme").then().statusCode(200).body("ROLLSTUHL", containsString("<circle"));
        given().get(BASIS + "/halte").then().statusCode(200).body("$", hasSize(4)).body("[0]", org.hamcrest.Matchers.equalTo("Hamburg"));
    }

    @Test
    void testAnsichtOhneUndMitFahrtabschnitt() {
        given().get(BASIS + "/" + LAYOUT + "/ansicht").then().statusCode(200)
                .body("breiteM", greaterThan(13f)).body("formen.size()", greaterThan(100)).body("ebenen[0]", notNullValue());
        given().queryParam("von", 0).queryParam("nach", 2).queryParam("faerbung", "KLASSE").get(BASIS + "/" + LAYOUT + "/ansicht")
                .then().statusCode(200).body("formen.find { it.details?.Platz == '3' }.stile", hasItem("zustand-frei"));
    }

    @Test
    void testUngueltigeAnfragen() {
        given().get(BASIS + "/gibt-es-nicht/ansicht").then().statusCode(404).body("fehler", containsString("gibt-es-nicht"));
        given().queryParam("von", 0).get(BASIS + "/" + LAYOUT + "/ansicht").then().statusCode(400);
        given().queryParam("von", 0).queryParam("nach", 9).get(BASIS + "/" + LAYOUT + "/ansicht").then().statusCode(400);
        given().queryParam("von", 2).queryParam("nach", 1).get(BASIS + "/" + LAYOUT + "/ansicht").then().statusCode(400);
    }

    @Test
    void testBelegenZeigtZustandProFahrtabschnittUndGibtFrei() {
        var zugfahrt = zugfahrt();

        var reservierungId = given().queryParam("zugfahrt", zugfahrt).contentType(ContentType.JSON).body(belegung(3, 0, 2))
                .post(BASIS + "/" + LAYOUT + "/belegungen").then().statusCode(201).body("sitzNummer", org.hamcrest.Matchers.equalTo(3))
                .extract().path("reservierungId");

        org.junit.jupiter.api.Assertions.assertTrue(stile(zugfahrt, 3, 0, 2).contains("zustand-belegt"));
        org.junit.jupiter.api.Assertions.assertTrue(stile(zugfahrt, 3, 1, 3).contains("zustand-teilweise"));
        org.junit.jupiter.api.Assertions.assertTrue(stile(zugfahrt, 3, 2, 3).contains("zustand-frei"));
        org.junit.jupiter.api.Assertions.assertTrue(stile(zugfahrt, 5, 0, 2).contains("zustand-frei"));

        /*  Überschneidung wird abgelehnt, ein anschließender Abschnitt ist frei  */
        given().queryParam("zugfahrt", zugfahrt).contentType(ContentType.JSON).body(belegung(3, 1, 3))
                .post(BASIS + "/" + LAYOUT + "/belegungen").then().statusCode(409).body("fehler", containsString("nicht frei"));
        given().queryParam("zugfahrt", zugfahrt).contentType(ContentType.JSON).body(belegung(3, 2, 3))
                .post(BASIS + "/" + LAYOUT + "/belegungen").then().statusCode(201);
        given().queryParam("zugfahrt", zugfahrt).get(BASIS + "/" + LAYOUT + "/belegungen").then().statusCode(200).body("$", hasSize(2));

        given().queryParam("zugfahrt", zugfahrt).delete(BASIS + "/" + LAYOUT + "/belegungen/" + reservierungId).then().statusCode(204);
        org.junit.jupiter.api.Assertions.assertTrue(stile(zugfahrt, 3, 0, 2).contains("zustand-frei"));
        given().queryParam("zugfahrt", zugfahrt).delete(BASIS + "/" + LAYOUT + "/belegungen/" + reservierungId).then().statusCode(404);
    }

    @Test
    void testBelegenValidiertPlatzUndAbschnitt() {
        var zugfahrt = zugfahrt();

        given().queryParam("zugfahrt", zugfahrt).contentType(ContentType.JSON).body(belegung(9999, 0, 1))
                .post(BASIS + "/" + LAYOUT + "/belegungen").then().statusCode(400).body("fehler", containsString("9999"));
        given().queryParam("zugfahrt", zugfahrt).contentType(ContentType.JSON).body(belegung(3, 2, 2))
                .post(BASIS + "/" + LAYOUT + "/belegungen").then().statusCode(400);
        given().queryParam("zugfahrt", zugfahrt).contentType(ContentType.JSON).body(belegung(3, 0, 9))
                .post(BASIS + "/" + LAYOUT + "/belegungen").then().statusCode(400);
        given().queryParam("zugfahrt", zugfahrt).contentType(ContentType.JSON).body(Map.of("vonHalt", 0, "nachHalt", 1))
                .post(BASIS + "/" + LAYOUT + "/belegungen").then().statusCode(400);
    }
}
