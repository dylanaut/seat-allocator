package kreyj.sitzplatzreservierung.dto;

import java.util.List;

/**
 * Kennzahlen der aktuellen Belegung eines Wagens. Die Auslastung bezieht sich auf Sitzplätze je Teilstrecke in Prozent;
 * Gruppen zählen ab zwei Sitzplätzen.
 */
public record KennzahlenResponse(
    double auslastungGesamt,
    List<Double> auslastungJeTeilstrecke,
    int reservierungen,
    int gruppen,
    int gruppenZusammenhaengend
) {
}
