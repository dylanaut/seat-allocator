package org.example.train.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record BestaetigungRequest(
    @NotEmpty @Size(max = 10) List<String> sitzIds,
    Integer einstiegHalt,
    Integer ausstiegHalt,
    String vormerkToken,
    Instant anfragezeitpunkt
) {
    public Instant wirksamerZeitpunkt() {
        return anfragezeitpunkt != null ? anfragezeitpunkt : Instant.now();
    }
}
