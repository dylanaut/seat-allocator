package org.example.train.dto;

import jakarta.validation.constraints.Min;
import java.time.Instant;

public record EinzelreservierungRequest(
    boolean wuenschtSteckdose,
    boolean wuenschtFenster,
    boolean wuenschtTisch,
    boolean wuenschtRuhezone,
    @Min(0) Integer einstiegHalt,
    @Min(0) Integer ausstiegHalt,
    Instant anfragezeitpunkt
) {
    public Instant wirksamerZeitpunkt() {
        return anfragezeitpunkt != null ? anfragezeitpunkt : Instant.now();
    }
}
