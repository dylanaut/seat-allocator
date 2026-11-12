package kreyj.sitzplatzreservierung.dto;

import kreyj.sitzplatzreservierung.model.Platzart;
import kreyj.sitzplatzreservierung.model.Sitzplatz;
import kreyj.sitzplatzreservierung.model.Zone;

public record SitzplatzAnsicht(
    String id,
    String bezeichnung,
    Platzart art,
    int reihe,
    int spalte,
    Zone zone,
    Sitzzustand zustand
) {
    public static SitzplatzAnsicht von(Sitzplatz sitzplatz, Sitzzustand zustand) {
        return new SitzplatzAnsicht(sitzplatz.id, sitzplatz.anzeigename(), sitzplatz.art, sitzplatz.reihe, sitzplatz.spalte, sitzplatz.zone, zustand);
    }
}
