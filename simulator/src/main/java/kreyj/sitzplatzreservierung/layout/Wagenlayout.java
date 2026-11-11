package kreyj.sitzplatzreservierung.layout;

import java.util.Comparator;
import java.util.List;

/**
 * Unveränderliches Layout eines Wagens in Metern.
 */
public record Wagenlayout(String kennung, double laengeM, double breiteM, Umriss kontur, List<Wagenelement> elemente) {

    public Wagenlayout {
        elemente = List.copyOf(elemente);
    }

    public <T extends Wagenelement> List<T> elemente(Class<T> art) {
        return elemente.stream().filter(art::isInstance).map(art::cast).toList();
    }

    public List<Wagenelement.Sitzplatz> sitzplaetze() {
        return elemente(Wagenelement.Sitzplatz.class);
    }

    public List<Ebene> ebenen() {
        return elemente.stream().map(Wagenelement::ebene).distinct().sorted(Comparator.naturalOrder()).toList();
    }
}
