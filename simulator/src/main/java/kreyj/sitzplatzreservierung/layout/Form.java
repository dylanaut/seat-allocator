package kreyj.sitzplatzreservierung.layout;

import java.util.List;
import java.util.Map;

/**
 * Eine zu zeichnende Form der Wagenansicht; Farben und Linienarten legt der Client über die Stilklassen fest.
 *
 * @param pfad         SVG-Pfaddaten in Metern, leer bei reinen Beschriftungen oder Piktogrammen
 * @param stile        CSS-Klassen, z. B. "schicht-sitz", "sitz", "farbe-erste", "zustand-belegt"
 * @param beschriftung optionaler Text
 * @param piktogramm   optionales Piktogramm
 * @param details      Angaben für die Detailanzeige
 */
public record Form(String id, Ebene ebene, String pfad, List<String> stile, Beschriftung beschriftung, Piktogramm piktogramm,
                   Map<String, String> details) {

    public record Beschriftung(String text, Punkt position, double groesse) {
    }

    public record Piktogramm(Piktogrammart art, Punkt position, double groesse) {
    }
}
