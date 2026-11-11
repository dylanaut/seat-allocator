package kreyj.sitzplatzreservierung.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BelegungsplanTest {

    private static Belegungsplan plan(Belegung... belegungen) {
        var plan = Belegungsplan.leer("Z1", 1);
        for (var belegung : belegungen)
            plan = plan.mit(belegung);
        return plan;
    }

    @Test
    void testAneinanderGrenzendeAbschnitteUeberschneidenSichNicht() {
        var plan = plan(new Belegung("R1", 10, new Fahrtabschnitt(0, 2), BelegungsArt.RESERVIERT));

        assertTrue(plan.istFrei(10, new Fahrtabschnitt(2, 4)));
        assertFalse(plan.istFrei(10, new Fahrtabschnitt(1, 3)));
        assertTrue(plan.istFrei(11, new Fahrtabschnitt(0, 2)));
        assertEquals(Platzzustand.FREI, plan.zustand(10, new Fahrtabschnitt(2, 4)));
    }

    @Test
    void testZustandTeilweiseBelegtOderVollstaendigBelegt() {
        var plan = plan(new Belegung("R1", 10, new Fahrtabschnitt(0, 2), BelegungsArt.RESERVIERT));

        assertEquals(Platzzustand.TEILWEISE, plan.zustand(10, new Fahrtabschnitt(1, 3)));
        assertEquals(Platzzustand.BELEGT, plan.zustand(10, new Fahrtabschnitt(0, 2)));
        assertEquals(Platzzustand.BELEGT, plan.zustand(10, new Fahrtabschnitt(1, 2)));
    }

    @Test
    void testAnschliessendeBelegungenDeckenGemeinsamDenAbschnittAb() {
        var plan = plan(new Belegung("R1", 10, new Fahrtabschnitt(0, 2), BelegungsArt.RESERVIERT),
                new Belegung("R2", 10, new Fahrtabschnitt(2, 4), BelegungsArt.RESERVIERT));

        assertEquals(Platzzustand.BELEGT, plan.zustand(10, new Fahrtabschnitt(0, 4)));
        assertEquals(Platzzustand.TEILWEISE, plan.zustand(10, new Fahrtabschnitt(0, 5)));
    }

    @Test
    void testRangfolgeSperreVorReservierungVorVormerkung() {
        var gewaehlt = new Fahrtabschnitt(0, 2);

        assertEquals(Platzzustand.VORGEMERKT,
                plan(new Belegung("V", 1, gewaehlt, BelegungsArt.VORGEMERKT)).zustand(1, gewaehlt));
        assertEquals(Platzzustand.BELEGT,
                plan(new Belegung("V", 1, gewaehlt, BelegungsArt.VORGEMERKT), new Belegung("R", 1, gewaehlt, BelegungsArt.RESERVIERT)).zustand(1, gewaehlt));
        assertEquals(Platzzustand.GESPERRT,
                plan(new Belegung("R", 1, gewaehlt, BelegungsArt.RESERVIERT), new Belegung("S", 1, gewaehlt, BelegungsArt.GESPERRT)).zustand(1, gewaehlt));
    }

    @Test
    void testPlanIstUnveraenderlich() {
        var leer = Belegungsplan.leer("Z1", 1);
        var belegt = leer.mit(new Belegung("R1", 1, new Fahrtabschnitt(0, 1), BelegungsArt.RESERVIERT));

        assertTrue(leer.belegungen().isEmpty());
        assertEquals(1, belegt.belegungen().size());
    }

    @Test
    void testUngueltigerFahrtabschnitt() {
        assertThrows(IllegalArgumentException.class, () -> new Fahrtabschnitt(2, 2));
        assertThrows(IllegalArgumentException.class, () -> new Fahrtabschnitt(-1, 2));
    }
}
