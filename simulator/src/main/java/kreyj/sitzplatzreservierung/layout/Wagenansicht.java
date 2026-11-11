package kreyj.sitzplatzreservierung.layout;

import java.util.List;

/**
 * Fertig gezeichnete Ansicht eines Wagens in Zeichenreihenfolge (hinten nach vorn).
 */
public record Wagenansicht(double breiteM, double hoeheM, List<Ebene> ebenen, List<Form> formen) {
}
