package kreyj.sitzplatzreservierung.layout;

/**
 * Piktogramme als SVG-Inhalt im 24er-Raster (Linienzeichnung, ohne Füllung).
 */
public enum Piktogrammart {
    ROLLSTUHL("<circle cx=\"10\" cy=\"4\" r=\"2\"/><path d=\"M10 7v7h6l3 6M10 11h5\"/><path d=\"M7 11a6 6 0 1 0 9 7\"/>"),
    FAHRRAD("<circle cx=\"5.5\" cy=\"16\" r=\"3.5\"/><circle cx=\"18.5\" cy=\"16\" r=\"3.5\"/><path d=\"M5.5 16 9 8h7l2.5 8M9 8l2.5 8L16 8M7.5 6.5h3\"/>"),
    KINDERWAGEN("<circle cx=\"7\" cy=\"19\" r=\"2\"/><circle cx=\"17\" cy=\"19\" r=\"2\"/><path d=\"M4 6h3l1.5 9H19a7 7 0 0 0-7-8H7.5\"/>"),
    GEPAECK("<rect x=\"5\" y=\"8\" width=\"14\" height=\"12\" rx=\"2\"/><path d=\"M9 8V5h6v3M9 12v5M15 12v5\"/>"),
    MUELLEIMER("<path d=\"M5 7h14M9 7V5h6v2M7 7l1 13h8l1-13M10 11v6M14 11v6\"/>"),
    MONITOR("<rect x=\"3\" y=\"5\" width=\"18\" height=\"12\" rx=\"1.5\"/><path d=\"M8 20h8M12 17v3\"/>"),
    KUPPLUNG("<circle cx=\"8\" cy=\"12\" r=\"4\"/><circle cx=\"16\" cy=\"12\" r=\"4\"/>"),
    FAHRERSTAND("<rect x=\"3\" y=\"7\" width=\"18\" height=\"10\" rx=\"2\"/><path d=\"M7 17v3M17 17v3M7 12h5\"/>"),
    PERSONAL("<circle cx=\"12\" cy=\"7\" r=\"3\"/><path d=\"M5 20a7 7 0 0 1 14 0z\"/>"),
    WICKELTISCH("<circle cx=\"12\" cy=\"6\" r=\"2\"/><rect x=\"3\" y=\"11\" width=\"18\" height=\"4\" rx=\"1\"/><path d=\"M6 15v5M18 15v5\"/>"),
    SCHLIESSFACH("<rect x=\"5\" y=\"4\" width=\"14\" height=\"16\" rx=\"1\"/><path d=\"M12 4v16M9.5 12h.01M14.5 12h.01\"/>"),
    GARDEROBE("<path d=\"M12 6a2 2 0 1 1 2 2l-2 2v2M4 20l8-8 8 8z\"/>"),
    KLAPPSITZ("<path d=\"M8 4v10h9M8 14l-2 6M17 14l2 6\"/>");

    private final String svg;

    Piktogrammart(String svg) {
        this.svg = svg;
    }

    public String svg() {
        return svg;
    }
}
