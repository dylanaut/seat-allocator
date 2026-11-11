package kreyj.sitzplatzreservierung.exception;

public class ReservierungNichtGefundenException extends RuntimeException {

    public ReservierungNichtGefundenException(String reservierungId) {
        super("Reservierung nicht gefunden: " + reservierungId);
    }
}
