package org.example.train;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.example.train.model.Wagen;
import org.example.train.model.Gruppenkontingent;
import org.example.train.model.Sitzplatz;
import org.example.train.model.Zone;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ZuteilungsregelnTest {

    private static final String SINGLE = """
        { "wuenschtSteckdose": false, "wuenschtFenster": false, "wuenschtTisch": false, "wuenschtRuhezone": false%s }
        """;

    private static final String GROUP = """
        { "gruppengroesse": %d, "wuenschtSteckdose": false, "wuenschtFenster": false, "wuenschtTisch": false, "wuenschtRuhezone": false }
        """;

    private static void wagen(String id) {
        Wagen c = new Wagen();
        c.id = id;
        c.zugnummer = "TEST";
        c.persist();
    }

    private static void sitzplatz(String wagenId, int reihe, int spalte, boolean table, Zone zone, String id) {
        Sitzplatz s = new Sitzplatz();
        s.id = wagenId + "-" + reihe + (char) ('A' + spalte);
        s.wagenId = wagenId;
        s.reihe = reihe;
        s.spalte = spalte;
        s.hatTisch = table;
        s.zone = zone;
        s.kontingentId = id;
        s.persist();
    }

    private static void reihen(String wagenId, int from, int to, boolean table, Zone zone, String id) {
        for (int r = from; r <= to; r++)
            for (int c = 0; c < 4; c++)
                sitzplatz(wagenId, r, c, table, zone, id);
    }

    private static void kontingent(String id, String wagenId, String deadline) {
        Gruppenkontingent b = new Gruppenkontingent();
        b.id = id;
        b.wagenId = wagenId;
        b.freigabefrist = Instant.parse(deadline);
        b.persist();
    }

    private static Response post(String wagenId, String path, String body) {
        return given().contentType(ContentType.JSON).body(body).post("/api/v1/wagen/" + wagenId + "/sitzplaetze/" + path);
    }

    private static String atSingle(String timestamp) {
        return SINGLE.formatted(", \"anfragezeitpunkt\": \"" + timestamp + "\"");
    }

    private static String atTable(String timestamp) {
        return atSingle(timestamp).replace("\"wuenschtTisch\": false", "\"wuenschtTisch\": true");
    }

    private static <T> List<T> parallel(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> t : tasks)
            futures.add(pool.submit(() -> {
                start.await();
                return t.call();
            }));
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures)
            results.add(f.get());
        pool.shutdown();
        return results;
    }

    @Test
    void testParallelSingleSeatsNeverDoubleBooked() throws Exception {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("T1");
            reihen("T1", 1, 2, false, Zone.STANDARD, null);
        });

        List<Callable<Response>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++)
            tasks.add(() -> post("T1", "zuteilen", SINGLE.formatted("")));

        Set<String> ids = new HashSet<>();
        for (Response r : parallel(tasks)) {
            assertEquals(200, r.statusCode());
            ids.add(r.jsonPath().getString("id"));
        }
        assertEquals(8, ids.size());
        assertEquals(409, post("T1", "zuteilen", SINGLE.formatted("")).statusCode());
    }

    @Test
    void testParallelGroupsNeverOverlap() throws Exception {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("T2");
            reihen("T2", 1, 4, false, Zone.STANDARD, null);
        });

        List<Callable<Response>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            tasks.add(() -> post("T2", "gruppe-zuteilen", GROUP.formatted(4)));

        List<String> booked = new ArrayList<>();
        for (Response r : parallel(tasks))
            if (r.statusCode() == 200) {
                assertEquals(4, r.jsonPath().getList("id").size());
                booked.addAll(r.jsonPath().getList("id"));
            }
            else
                assertEquals(409, r.statusCode());

        assertTrue(booked.size() >= 4);
        assertEquals(booked.size(), new HashSet<>(booked).size());
    }

    @Test
    void testTopologyProtectionBeforeDeadline() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("T3");
            kontingent("T3_B", "T3", "2030-01-01T00:00:00Z");
            reihen("T3", 1, 1, false, Zone.STANDARD, null);
            reihen("T3", 2, 2, false, Zone.STANDARD, "T3_B");
        });

        // Frei: Reihe 1 (4 Sitze); der erste Einzelplatz lässt 3 = K übrig, der zweite würde 2 hinterlassen
        assertEquals(200, post("T3", "zuteilen", atSingle("2029-12-31T23:00:00Z")).statusCode());
        assertEquals(409, post("T3", "zuteilen", atSingle("2029-12-31T23:00:00Z")).statusCode());
        assertEquals(200, post("T3", "zuteilen", atSingle("2030-01-02T00:00:00Z")).statusCode());
    }

    @Test
    void testOnlyGroupsOfThreeMayUseBlockedSeats() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("T9");
            kontingent("T9_B", "T9", "2030-01-01T00:00:00Z");
            sitzplatz("T9", 1, 0, true, Zone.STANDARD, "T9_B");
            sitzplatz("T9", 1, 1, true, Zone.STANDARD, "T9_B");
            sitzplatz("T9", 1, 2, true, Zone.STANDARD, "T9_B");
        });

        String at = ", \"anfragezeitpunkt\": \"2029-12-31T12:00:00Z\" }";
        assertEquals(409, post("T9", "zuteilen", atSingle("2029-12-31T12:00:00Z")).statusCode());
        assertEquals(409, post("T9", "gruppe-zuteilen", GROUP.formatted(2).replace(" }\n", at)).statusCode());
        assertEquals(200, post("T9", "gruppe-zuteilen", GROUP.formatted(3).replace(" }\n", at)).statusCode());
    }

    @Test
    void testTimeDecayAvoidsTableFarFromDeadline() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("T4");
            kontingent("T4_B", "T4", "2030-01-01T00:00:00Z");
            sitzplatz("T4", 1, 0, true, Zone.STANDARD, null);
            sitzplatz("T4", 5, 0, false, Zone.STANDARD, null);
            sitzplatz("T4", 9, 0, false, Zone.STANDARD, "T4_B");
        });

        // 1 h nach Beginn des 24-h-Horizonts: Malus ~2.9 übersteigt den Tischbonus 1.5
        post("T4", "zuteilen", atTable("2029-12-31T01:00:00Z")).then().statusCode(200).body("id", org.hamcrest.Matchers.is("T4-5A"));
    }

    @Test
    void testTimeDecayVanishesAtDeadline() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("T5");
            kontingent("T5_B", "T5", "2030-01-01T00:00:00Z");
            sitzplatz("T5", 1, 0, true, Zone.STANDARD, null);
            sitzplatz("T5", 5, 0, false, Zone.STANDARD, null);
            sitzplatz("T5", 9, 0, false, Zone.STANDARD, "T5_B");
        });

        // 1 Minute vor der Deadline: Malus ~0, der Tischwunsch (Bonus 1.5) setzt sich durch
        post("T5", "zuteilen", atTable("2029-12-31T23:59:00Z")).then().statusCode(200).body("id", org.hamcrest.Matchers.is("T5-1A"));
    }

    @Test
    void testQuietZoneIsFallbackWithoutWishButHardWithWish() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("T6");
            sitzplatz("T6", 1, 0, false, Zone.RUHE, null);
            wagen("T7");
            sitzplatz("T7", 1, 0, false, Zone.STANDARD, null);
        });

        post("T6", "zuteilen", SINGLE.formatted("")).then().statusCode(200).body("zone", org.hamcrest.Matchers.is("RUHE"));

        String quietWish = SINGLE.formatted("").replace("\"wuenschtRuhezone\": false", "\"wuenschtRuhezone\": true");
        assertEquals(409, post("T7", "zuteilen", quietWish).statusCode());
    }

    @Test
    void testClusterSearchRuntimeForLargeGroupInFullCarriage() {
        QuarkusTransaction.requiringNew().run(() -> {
            wagen("T8");
            reihen("T8", 1, 16, false, Zone.STANDARD, null);
        });

        long start = System.nanoTime();
        Response r = post("T8", "gruppe-zuteilen", GROUP.formatted(8));
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("CLUSTER-RUNTIME n=8, 64 Sitze: " + millis + " ms (inkl. HTTP + DB)");
        assertEquals(200, r.statusCode());
        assertTrue(millis < 10_000, "Cluster-Suche zu langsam: " + millis + " ms");
    }
}
