package kreyj.sitzplatzreservierung.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Schritt eines Szenarios: eine Reservierungsanfrage (wird automatisch bestätigt) oder die Stornierung der
 * Reservierung eines früheren Schritts (bezugSchritt, 1-basiert).
 */
public record Szenarioschritt(
    @NotNull Schrittart typ,
    String bezeichnung,
    @Valid GruppenreservierungRequest anfrage,
    @Min(1) Integer bezugSchritt
) {
}
