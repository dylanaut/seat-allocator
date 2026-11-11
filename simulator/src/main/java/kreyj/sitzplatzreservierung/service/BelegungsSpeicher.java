package kreyj.sitzplatzreservierung.service;

import jakarta.enterprise.context.ApplicationScoped;
import kreyj.sitzplatzreservierung.exception.ReservierungNichtGefundenException;
import kreyj.sitzplatzreservierung.exception.ReservierungsKonfliktException;
import kreyj.sitzplatzreservierung.layout.Belegung;
import kreyj.sitzplatzreservierung.layout.Belegungsplan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hält die Belegungspläne der Wagenlayouts je Zugfahrt im Speicher. Die Halte sind für alle Zugfahrten dieselben Demo-Halte,
 * ein Fahrtabschnitt bezieht sich auf deren Indizes.
 */
@ApplicationScoped
public class BelegungsSpeicher {

    static final List<String> DEMO_HALTE = List.of("Hamburg", "Hannover", "Frankfurt", "München");

    private final Map<String, Belegungsplan> plaene = new HashMap<>();

    public List<String> halte(String zugfahrt) {
        return DEMO_HALTE;
    }

    public synchronized Belegungsplan plan(String zugfahrt, String kennung) {
        return plaene.getOrDefault(schluessel(zugfahrt, kennung), Belegungsplan.leer(zugfahrt, 0));
    }

    /**
     * @throws ReservierungsKonfliktException wenn der Platz im Fahrtabschnitt schon belegt, vorgemerkt oder gesperrt ist
     */
    public synchronized Belegung belegen(String zugfahrt, String kennung, Belegung belegung) {
        var plan = plan(zugfahrt, kennung);
        if (!plan.istFrei(belegung.sitzNummer(), belegung.abschnitt()))
            throw new ReservierungsKonfliktException("Platz " + belegung.sitzNummer() + " ist im Fahrtabschnitt "
                    + belegung.abschnitt().vonHalt() + " bis " + belegung.abschnitt().nachHalt() + " nicht frei");
        plaene.put(schluessel(zugfahrt, kennung), plan.mit(belegung));
        return belegung;
    }

    /**
     * Gibt alle Belegungen einer Reservierung frei.
     */
    public synchronized void freigeben(String zugfahrt, String kennung, String reservierungId) {
        var plan = plan(zugfahrt, kennung);
        var neu = plan.ohne(reservierungId);
        if (neu.belegungen().size() == plan.belegungen().size())
            throw new ReservierungNichtGefundenException(reservierungId);
        plaene.put(schluessel(zugfahrt, kennung), neu);
    }

    private static String schluessel(String zugfahrt, String kennung) {
        return zugfahrt + "|" + kennung;
    }
}
