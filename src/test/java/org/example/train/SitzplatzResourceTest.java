package org.example.train;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
class SitzplatzResourceTest {

    @Test
    void testAllocateQuietZoneSeat() {
        String payload = """
            {
                "wuenschtSteckdose": true,
                "wuenschtFenster": true,
                "wuenschtTisch": false,
                "wuenschtRuhezone": true,
                "anfragezeitpunkt": "2026-11-03T12:00:00Z"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/v1/wagen/W1/sitzplaetze/zuteilen")
            .then()
            .statusCode(200)
            // 1A und 1D (Strom + Fenster) sind gleichwertig; die Dispersion zu bereits belegten Sitzen entscheidet
            .body("id", anyOf(is("1A"), is("1D")))
            .body("zone", is("RUHE"));
    }

    @Test
    void testSingleSeatNeverFromBlockedTableRowBeforeDeadline() {
        // Einzelplatz vor der Deadline von 18:00 Uhr: Reihe 4 (Tische) ist für Gruppen n >= 3 reserviert
        String payload = """
            {
                "wuenschtSteckdose": false,
                "wuenschtFenster": false,
                "wuenschtTisch": true,
                "wuenschtRuhezone": false,
                "anfragezeitpunkt": "2026-11-03T15:00:00Z"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/v1/wagen/W1/sitzplaetze/zuteilen")
            .then()
            .statusCode(200)
            .body("id", not(startsWith("4")));
    }

    @Test
    void testGroupOfThreeIsAllocated() {
        String payload = """
            {
                "gruppengroesse": 3,
                "wuenschtSteckdose": false,
                "wuenschtFenster": false,
                "wuenschtTisch": true,
                "wuenschtRuhezone": false,
                "anfragezeitpunkt": "2026-11-03T15:00:00Z"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/v1/wagen/W1/sitzplaetze/gruppe-zuteilen")
            .then()
            .statusCode(200)
            .body("", hasSize(3));
    }

    @Test
    void testUnknownCarriage() {
        String payload = """
            {
                "wuenschtSteckdose": false,
                "wuenschtFenster": false,
                "wuenschtTisch": false,
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
