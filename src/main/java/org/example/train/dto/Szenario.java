package org.example.train.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record Szenario(
    String id,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 1000) String beschreibung,
    @NotBlank String wagenId,
    @NotNull @Size(max = 200) List<@Valid Szenarioschritt> schritte
) {
}
