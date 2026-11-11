package kreyj.sitzplatzreservierung.layout;

/**
 * Ebene eines Wagens; Doppelstockwagen haben mehrere, deckungsgleich übereinanderliegende Ebenen.
 */
public enum Ebene implements RismapsSchluessel {
    OBERDECK("UPPER_DECK"),
    HAUPTEBENE("DEFAULT_DECK"),
    UNTERDECK("LOWER_DECK");

    private final String schluessel;

    Ebene(String schluessel) {
        this.schluessel = schluessel;
    }

    @Override
    public String schluessel() {
        return schluessel;
    }
}
