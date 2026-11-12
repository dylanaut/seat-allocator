package kreyj.sitzplatzreservierung;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
class SitzplatzResourceTest {

    @Test
    void testAllocateQuietZoneSeat() {
        String payload = """
            {
                "wuenschtRuhezone": true,
                "anfragezeitpunkt": "2026-11-03T12:00:00Z"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/v1/wagen/Beispiel-Mittel/sitzplaetze/zuteilen")
            .then()
            .statusCode(200)
            .body("id", startsWith("Beispiel-Mittel-"))
            .body("zone", is("RUHE"));
    }

    @Test
    void testSingleSeatNeverFromBlockedTableRowBeforeDeadline() {
        // Einzelplatz vor der Freigabefrist: die Plätze der Tischgruppe sind für Gruppen n >= 3 reserviert
        String payload = """
            {
                "wuenschtRuhezone": false,
                "anfragezeitpunkt": "2026-11-03T15:00:00Z"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/v1/wagen/Beispiel-Mittel/sitzplaetze/zuteilen")
            .then()
            .statusCode(200)
            .body("id", not(in(List.of("Beispiel-Mittel-10", "Beispiel-Mittel-11", "Beispiel-Mittel-13", "Beispiel-Mittel-14"))));
    }

    @Test
    void testGroupOfThreeIsAllocated() {
        String payload = """
            {
                "gruppengroesse": 3,
                "wuenschtRuhezone": false,
                "anfragezeitpunkt": "2026-11-03T15:00:00Z"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/v1/wagen/Beispiel-Mittel/sitzplaetze/gruppe-zuteilen")
            .then()
            .statusCode(200)
            .body("", hasSize(3));
    }

    @Test
    void testUnknownCarriage() {
        String payload = """
            {
                "wuenschtRuhezone": false
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/v1/wagen/XX/sitzplaetze/zuteilen")
            .then()
            .statusCode(404);
    }
}
