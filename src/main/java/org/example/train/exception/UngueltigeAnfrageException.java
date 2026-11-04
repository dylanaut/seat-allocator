package org.example.train.exception;

public class UngueltigeAnfrageException extends RuntimeException {

    public UngueltigeAnfrageException(String meldung) {
        super(meldung);
    }
}
