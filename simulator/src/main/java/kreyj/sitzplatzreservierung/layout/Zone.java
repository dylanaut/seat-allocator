package kreyj.sitzplatzreservierung.layout;

/**
 * Bereich des Wagens, dem ein Sitzplatz oder eine Fläche zugeordnet ist.
 */
public enum Zone implements RismapsSchluessel {
    STANDARD("MOBILE"),
    SONDERNUTZUNG("SPECIAL_USAGE"),
    RUHE("SILENCE"),
    FAMILIE("FAMILY"),
    KLEINKIND("INFANT"),
    ROLLSTUHL("WHEELCHAIR_ACCESSIBLE"),
    PERSONAL("STAFF");

    private final String schluessel;

    Zone(String schluessel) {
        this.schluessel = schluessel;
    }

    @Override
    public String schluessel() {
        return schluessel;
    }
}
