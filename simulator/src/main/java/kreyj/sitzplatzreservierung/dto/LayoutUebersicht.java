package kreyj.sitzplatzreservierung.dto;

import kreyj.sitzplatzreservierung.layout.Ebene;

import java.util.List;

public record LayoutUebersicht(String kennung, double laengeM, double breiteM, int sitzplaetze, List<Ebene> ebenen) {
}
