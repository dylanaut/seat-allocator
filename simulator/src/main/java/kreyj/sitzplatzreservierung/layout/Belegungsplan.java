package kreyj.sitzplatzreservierung.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Unveränderliche Belegung der Sitzplätze eines Wagens für eine Zugfahrt.
 */
public record Belegungsplan(String zugfahrtId, int wagenordnungsnummer, List<Belegung> belegungen) {

    public Belegungsplan {
        belegungen = List.copyOf(belegungen);
    }

    public static Belegungsplan leer(String zugfahrtId, int wagenordnungsnummer) {
        return new Belegungsplan(zugfahrtId, wagenordnungsnummer, List.of());
    }

    public Belegungsplan mit(Belegung belegung) {
        var neu = new ArrayList<>(belegungen);
        neu.add(belegung);
        return new Belegungsplan(zugfahrtId, wagenordnungsnummer, neu);
    }

    public Belegungsplan ohne(String reservierungId) {
        return new Belegungsplan(zugfahrtId, wagenordnungsnummer,
                belegungen.stream().filter((belegung) -> !belegung.reservierungId().equals(reservierungId)).toList());
    }

    public boolean istFrei(int sitzNummer, Fahrtabschnitt abschnitt) {
        return ueberschneidende(sitzNummer, abschnitt).isEmpty();
    }

    /**
     * FREI ohne Überschneidung; sonst, wenn die Belegungen jeden Halteabschnitt der Anfrage abdecken, GESPERRT, BELEGT oder
     * VORGEMERKT (in dieser Rangfolge), andernfalls TEILWEISE.
     */
    public Platzzustand zustand(int sitzNummer, Fahrtabschnitt gewaehlt) {
        var relevante = ueberschneidende(sitzNummer, gewaehlt);
        if (relevante.isEmpty())
            return Platzzustand.FREI;

        var abgedeckt = new boolean[gewaehlt.nachHalt() - gewaehlt.vonHalt()];
        for (var belegung : relevante)
            for (int halt = Math.max(belegung.abschnitt().vonHalt(), gewaehlt.vonHalt());
                 halt < Math.min(belegung.abschnitt().nachHalt(), gewaehlt.nachHalt()); halt++)
                abgedeckt[halt - gewaehlt.vonHalt()] = true;
        for (boolean abschnittAbgedeckt : abgedeckt)
            if (!abschnittAbgedeckt)
                return Platzzustand.TEILWEISE;

        if (relevante.stream().anyMatch((belegung) -> belegung.art() == BelegungsArt.GESPERRT))
            return Platzzustand.GESPERRT;
        if (relevante.stream().anyMatch((belegung) -> belegung.art() == BelegungsArt.RESERVIERT))
            return Platzzustand.BELEGT;
        return Platzzustand.VORGEMERKT;
    }

    private List<Belegung> ueberschneidende(int sitzNummer, Fahrtabschnitt abschnitt) {
        return belegungen.stream()
                .filter((belegung) -> belegung.sitzNummer() == sitzNummer && belegung.abschnitt().ueberschneidet(abschnitt))
                .toList();
    }
}
