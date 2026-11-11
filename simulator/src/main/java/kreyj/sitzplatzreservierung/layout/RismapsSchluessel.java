package kreyj.sitzplatzreservierung.layout;

/**
 * Enum-Wert, der einem Schlüsselwort der RISMAPS-JSON-Dateien entspricht (z. B. TABLE_LONG).
 */
interface RismapsSchluessel {

    String schluessel();

    static <E extends Enum<E> & RismapsSchluessel> E von(Class<E> art, String schluessel, E unbekannt) {
        for (E wert : art.getEnumConstants())
            if (wert.schluessel() != null && wert.schluessel().equals(schluessel))
                return wert;
        return unbekannt;
    }
}
