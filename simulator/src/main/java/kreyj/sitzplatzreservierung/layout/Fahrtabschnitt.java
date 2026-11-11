package kreyj.sitzplatzreservierung.layout;

/**
 * Halbintervall über die Halteindizes einer Zugfahrt: von (inklusive) bis nach (exklusive).
 */
public record Fahrtabschnitt(int vonHalt, int nachHalt) {

    public Fahrtabschnitt {
        if (vonHalt < 0 || nachHalt <= vonHalt)
            throw new IllegalArgumentException("Ungültiger Fahrtabschnitt: " + vonHalt + " bis " + nachHalt);
    }

    public boolean ueberschneidet(Fahrtabschnitt anderer) {
        return vonHalt < anderer.nachHalt && anderer.vonHalt < nachHalt;
    }
}
