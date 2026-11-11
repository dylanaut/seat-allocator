package kreyj.sitzplatzreservierung.dto;

import java.time.Instant;
import java.util.List;

public record ReservierungResponse(
    String reservierungId,
    String wagenId,
    List<String> sitzIds,
    int einstiegHalt,
    int ausstiegHalt,
    int gruppengroesse,
    Instant erstelltAm
) {
}
