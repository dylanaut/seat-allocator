package org.example.train.exception;

public class WagenNichtGefundenException extends IllegalArgumentException {

    public WagenNichtGefundenException(String wagenId) {
        super("Wagen nicht gefunden: " + wagenId);
    }
}
