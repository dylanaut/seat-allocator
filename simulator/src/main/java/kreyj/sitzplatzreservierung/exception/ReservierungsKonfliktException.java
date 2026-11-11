package kreyj.sitzplatzreservierung.exception;

public class ReservierungsKonfliktException extends RuntimeException {

    public ReservierungsKonfliktException(String meldung) {
        super(meldung);
    }
}
