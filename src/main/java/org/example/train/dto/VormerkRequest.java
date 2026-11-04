package org.example.train.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Ersetzt die Vormerkung des Tokens durch die angegebenen Sitzplätze; eine leere Liste gibt sie frei.
 */
public record VormerkRequest(
    String vormerkToken,
    @NotNull @Size(max = 10) List<String> sitzIds,
    Integer gruppengroesse,
    boolean wuenschtRuhezone,
    boolean wuenschtRollstuhlplatz,
    boolean wuenschtFahrradplatz,
    boolean mitKindern,
    Integer einstiegHalt,
    Integer ausstiegHalt,
    Instant anfragezeitpunkt
) {
    public Instant wirksamerZeitpunkt() {
        return anfragezeitpunkt != null ? anfragezeitpunkt : Instant.now();
    }
}
