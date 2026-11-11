package kreyj.sitzplatzreservierung.layout;

import java.util.Set;

/**
 * Element eines Wagenlayouts. Das Layout ist unveränderlich und enthält keine Belegung.
 */
public sealed interface Wagenelement {

    String id();

    Ebene ebene();

    boolean nurPersonal();

    /**
     * @param nummer             Platznummer, 0 bei Sitzen ohne Nummer (z. B. Personalsitze), die nicht reserviert werden können
     * @param lehnenrichtungGrad Winkel gegen den Uhrzeigersinn ab "oben", zu dem die Rückenlehne zeigt
     */
    record Sitzplatz(String id, Ebene ebene, boolean nurPersonal, int nummer, Klasse klasse, Bauart bauart,
                     int lehnenrichtungGrad, Zone zone, Set<Sitzmerkmal> merkmale, Umriss umriss) implements Wagenelement {

        public Sitzplatz {
            merkmale = Set.copyOf(merkmale);
        }
    }

    record Tisch(String id, Ebene ebene, boolean nurPersonal, Tischart art, Umriss umriss) implements Wagenelement {
    }

    record Einbau(String id, Ebene ebene, boolean nurPersonal, Einbauart art, Umriss umriss) implements Wagenelement {
    }

    record Bereich(String id, Ebene ebene, boolean nurPersonal, Bereichsart art, Umriss umriss) implements Wagenelement {
    }

    record Zonenflaeche(String id, Ebene ebene, boolean nurPersonal, Zone zone, Umriss umriss) implements Wagenelement {
    }

    record Tuer(String id, Ebene ebene, boolean nurPersonal, Tuerart art, Punkt von, Punkt nach) implements Wagenelement {
    }

    record Ausstattung(String id, Ebene ebene, boolean nurPersonal, Ausstattungsart art, String name, Punkt position) implements Wagenelement {
    }

    /**
     * Art eines Tisches.
     */
    public enum Tischart implements RismapsSchluessel {
        LANG("TABLE_LONG"),
        KURZ("TABLE_SHORT"),
        HALB("TABLE_HALF"),
        SPEISE_LANG("TABLE_DINING_LONG"),
        SPEISE_KURZ("TABLE_DINING_SHORT"),
        BISTRO("BARTABLE_BISTRO"),
        WICKEL("TABLE_CHANGE"),
        ROLLSTUHL("TABLE_WHEELCHAIR"),
        SONSTIGE(null);

        private final String schluessel;

        Tischart(String schluessel) {
            this.schluessel = schluessel;
        }

        @Override
        public String schluessel() {
            return schluessel;
        }
    }

    /**
     * Art eines Einbaus.
     */
    public enum Einbauart implements RismapsSchluessel {
        THEKE("COUNTER"),
        WC("WC"),
        WASCHBECKEN("WASH_BASIN"),
        GEPAECKABLAGE("BAGGAGE_RACK"),
        ABLAGE("RACK"),
        REGAL("SHELF"),
        STAURAUM("STORAGE"),
        ARBEITSPLATZ("WORKSPACE"),
        KLAPPSITZ("FOLDING_SEAT"),
        SONSTIGE(null);

        private final String schluessel;

        Einbauart(String schluessel) {
            this.schluessel = schluessel;
        }

        @Override
        public String schluessel() {
            return schluessel;
        }
    }

    /**
     * Art einer Fläche.
     */
    public enum Bereichsart implements RismapsSchluessel {
        BODEN("FLOOR"),
        DURCHGANG("TRANSIT"),
        WAND("WALL"),
        FENSTER("WINDOW"),
        TECHNIK("TECHNICS"),
        STRUKTUR("STRUCTURE"),
        AUSSTIEG("EXIT_DOOR"),
        AUSSTIEGSTREPPE("EXIT_STAIRS"),
        TREPPE("STAIRS"),
        RAMPE("RAMP"),
        SITZBEREICH("SEAT"),
        GEPAECK("BAGGAGE"),
        ROLLSTUHL("WHEELCHAIR"),
        KINDERWAGEN("PRAM"),
        FAHRRAD("BIKE"),
        KLAPPSITZ("FOLDING_SEAT"),
        STEHPLATZ("STANCE"),
        SONSTIGE(null);

        private final String schluessel;

        Bereichsart(String schluessel) {
            this.schluessel = schluessel;
        }

        @Override
        public String schluessel() {
            return schluessel;
        }
    }

    /**
     * Art einer Öffnung.
     */
    public enum Tuerart implements RismapsSchluessel {
        AUSSTIEG("EXIT_DOOR"),
        TUER("DOOR"),
        DURCHGANG("OPEN");

        private final String schluessel;

        Tuerart(String schluessel) {
            this.schluessel = schluessel;
        }

        @Override
        public String schluessel() {
            return schluessel;
        }
    }

    /**
     * Art einer punktförmigen Ausstattung.
     */
    public enum Ausstattungsart implements RismapsSchluessel {
        MONITOR("INFO_DISPLAY"),
        KUPPLUNG("COUPLER"),
        MUELLEIMER("TRASHBIN"),
        FAHRERSTAND("CONTROL_STAND"),
        KINDERWAGENPLATZ("PRAM_PARKING"),
        PERSONAL("SERVICE_TEAM"),
        WICKELTISCH("CHANGING_TABLE"),
        SCHLIESSFACH("LOCKER"),
        GARDEROBE("WARDROBE"),
        ZEICHENHILFE(null),
        SONSTIGE(null);

        private final String schluessel;

        Ausstattungsart(String schluessel) {
            this.schluessel = schluessel;
        }

        @Override
        public String schluessel() {
            return schluessel;
        }
    }
}
