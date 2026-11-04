package org.example.train.dto;

import org.example.train.model.Platzart;
import org.example.train.model.Sitzplatz;
import org.example.train.model.Zone;

public record SitzplatzResponse(
    String id,
    Platzart art,
    int reihe,
    int spalte,
    boolean hatSteckdose,
    boolean amFenster,
    boolean hatTisch,
    Zone zone
) {
    public static SitzplatzResponse von(Sitzplatz sitzplatz) {
        return new SitzplatzResponse(
            sitzplatz.id,
            sitzplatz.art,
            sitzplatz.reihe,
            sitzplatz.spalte,
            sitzplatz.hatSteckdose,
            sitzplatz.amFenster,
            sitzplatz.hatTisch,
            sitzplatz.zone
        );
    }
}
