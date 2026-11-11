package kreyj.sitzplatzreservierung.layout;

/**
 * Bauart eines Sitzplatzes.
 */
public enum Bauart implements RismapsSchluessel {
    EINZEL("SINGLE"),
    DOPPEL("DOUBLE"),
    BANK("BENCH"),
    KLAPPSITZ("FOLDING_SEAT");

    private final String schluessel;

    Bauart(String schluessel) {
        this.schluessel = schluessel;
    }

    @Override
    public String schluessel() {
        return schluessel;
    }
}
