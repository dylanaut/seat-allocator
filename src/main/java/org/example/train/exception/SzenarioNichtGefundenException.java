package org.example.train.exception;

public class SzenarioNichtGefundenException extends RuntimeException {

    public SzenarioNichtGefundenException(String id) {
        super("Szenario nicht gefunden: " + id);
    }
}
