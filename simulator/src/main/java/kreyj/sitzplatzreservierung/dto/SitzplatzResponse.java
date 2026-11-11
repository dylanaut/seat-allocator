package kreyj.sitzplatzreservierung.dto;

import kreyj.sitzplatzreservierung.model.Platzart;
import kreyj.sitzplatzreservierung.model.Sitzplatz;
import kreyj.sitzplatzreservierung.model.Zone;

public record SitzplatzResponse(
    String id,
    Platzart art,
    int reihe,
    int spalte,
    Zone zone
) {
    public static SitzplatzResponse von(Sitzplatz sitzplatz) {
        return new SitzplatzResponse(
            sitzplatz.id,
            sitzplatz.art,
            sitzplatz.reihe,
            sitzplatz.spalte,
            sitzplatz.zone
        );
    }
}
