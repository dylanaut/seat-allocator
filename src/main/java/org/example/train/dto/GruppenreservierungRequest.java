package org.example.train.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;

public record GruppenreservierungRequest(
    @Min(1) @Max(8) int gruppengroesse,
    boolean wuenschtSteckdose,
    boolean wuenschtFenster,
    boolean wuenschtTisch,
    boolean wuenschtRuhezone,
    boolean wuenschtRollstuhlplatz,
    boolean wuenschtFahrradplatz,
    boolean mitKindern,
    @Min(0) Integer einstiegHalt,
    @Min(0) Integer ausstiegHalt,
    String vormerkToken,
    Instant anfragezeitpunkt
) {
    public Instant wirksamerZeitpunkt() {
        return anfragezeitpunkt != null ? anfragezeitpunkt : Instant.now();
    }
}
