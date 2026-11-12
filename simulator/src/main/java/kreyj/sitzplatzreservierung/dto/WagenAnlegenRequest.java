package kreyj.sitzplatzreservierung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param id                 Id des neuen Wagens; ohne Angabe die Kennung des Layouts
 * @param halte              Halte in Fahrtrichtung; ohne Angabe die Demo-Strecke
 * @param gruppenkontingent  legt für die größte Tischgruppe des Layouts ein Gruppenkontingent an
 */
public record WagenAnlegenRequest(
    @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-][A-Za-z0-9._-]*") String id,
    @NotBlank String layout,
    @Size(max = 100) String zugnummer,
    @Size(min = 2, max = 30) List<@NotBlank String> halte,
    boolean gruppenkontingent
) {
}
