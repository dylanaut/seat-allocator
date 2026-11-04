package org.example.train.exception;

public class ReservierungsKonfliktException extends RuntimeException {

    public ReservierungsKonfliktException(String meldung) {
        super(meldung);
    }
}
