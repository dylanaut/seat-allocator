package kreyj.sitzplatzreservierung.layout;

import java.util.List;

/**
 * Fertig gezeichnete Ansicht eines Wagens in Zeichenreihenfolge (hinten nach vorn).
 *
 * @param breiteM     Länge des Wagens in Metern (Breite der vollständigen Ansicht)
 * @param ausschnitte sichtbarer Längsbereich je Ebene; leere Wagenenden außerhalb sind weggelassen
 */
public record Wagenansicht(double breiteM, double hoeheM, List<Ebene> ebenen, List<Ausschnitt> ausschnitte, List<Form> formen) {

    /**
     * Sichtbarer Bereich einer Ebene in Längsrichtung des Wagens (x in Metern).
     */
    public record Ausschnitt(Ebene ebene, double vonXM, double bisXM) {
    }
}
