package kreyj.sitzplatzreservierung.exception;

public class WagenNichtGefundenException extends IllegalArgumentException {

    public WagenNichtGefundenException(String wagenId) {
        super("Wagen nicht gefunden: " + wagenId);
    }
}
