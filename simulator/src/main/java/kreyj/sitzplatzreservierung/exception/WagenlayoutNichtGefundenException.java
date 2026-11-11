package kreyj.sitzplatzreservierung.exception;

public class WagenlayoutNichtGefundenException extends IllegalArgumentException {

    public WagenlayoutNichtGefundenException(String kennung) {
        super("Wagenlayout nicht gefunden: " + kennung);
    }
}
