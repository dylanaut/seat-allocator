package org.example.train.dto;

import org.example.train.model.Wagen;

import java.util.List;

public record WagenResponse(String id, String zugnummer, List<String> halte) {

    public static WagenResponse von(Wagen wagen) {
        return new WagenResponse(wagen.id, wagen.zugnummer, wagen.halteNamen());
    }
}
