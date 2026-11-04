package org.example.train.dto;

import java.time.Instant;
import java.util.List;

public record SitzplanResponse(
    String wagenId,
    int einstiegHalt,
    int ausstiegHalt,
    List<SitzplatzAnsicht> sitzplaetze,
    List<String> vorgeschlagen,
    String meldung,
    String vormerkToken,
    Instant vormerkungAblauf
) {
}
