package kreyj.sitzplatzreservierung.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import kreyj.sitzplatzreservierung.layout.BelegungsArt;

public record LayoutBelegungRequest(
    String reservierungId,
    @NotNull @Min(1) Integer sitzNummer,
    @NotNull @Min(0) Integer vonHalt,
    @NotNull @Min(1) Integer nachHalt,
    BelegungsArt art
) {
}
