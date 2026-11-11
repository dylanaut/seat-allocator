package kreyj.sitzplatzreservierung.layout;

public record Belegung(String reservierungId, int sitzNummer, Fahrtabschnitt abschnitt, BelegungsArt art) {
}
