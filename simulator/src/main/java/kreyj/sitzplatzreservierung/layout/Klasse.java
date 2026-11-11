package kreyj.sitzplatzreservierung.layout;

/**
 * Wagenklasse eines Sitzplatzes.
 */
public enum Klasse implements RismapsSchluessel {
    ERSTE("FIRST"),
    ZWEITE("ECONOMY"),
    KEINE(null);

    private final String schluessel;

    Klasse(String schluessel) {
        this.schluessel = schluessel;
    }

    @Override
    public String schluessel() {
        return schluessel;
    }
}
