package org.example.train.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.train.dto.BestaetigungRequest;
import org.example.train.dto.GruppenreservierungRequest;
import org.example.train.dto.KennzahlenResponse;
import org.example.train.dto.VormerkRequest;
import org.example.train.dto.SitzplanResponse;
import org.example.train.dto.EinzelreservierungRequest;
import org.example.train.dto.ReservierungResponse;
import org.example.train.dto.Sitzzustand;
import org.example.train.dto.SitzplatzAnsicht;
import org.example.train.exception.ReservierungNichtGefundenException;
import org.example.train.exception.WagenNichtGefundenException;
import org.example.train.exception.UngueltigeAnfrageException;
import org.example.train.exception.ReservierungsKonfliktException;
import org.example.train.model.Wagen;
import org.example.train.model.Gruppenkontingent;
import org.example.train.model.Platzart;
import org.example.train.model.Sitzplatz;
import org.example.train.model.Vormerkung;
import org.example.train.model.Sitzreservierung;
import org.example.train.model.Zone;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class SitzzuteilungService {

    @ConfigProperty(name = "zuteilung.gewicht.praeferenz", defaultValue = "1.0")
    double gewichtPraeferenz;

    @ConfigProperty(name = "zuteilung.gewicht.streuung", defaultValue = "1.2")
    double gewichtStreuung;

    @ConfigProperty(name = "zuteilung.gewicht.kohaesion", defaultValue = "2.5")
    double gewichtKohaesion;

    @ConfigProperty(name = "zuteilung.gewicht.kinderbereich", defaultValue = "3.0")
    double gewichtKinderbereich;

    @ConfigProperty(name = "zuteilung.gewicht.zeitverfall", defaultValue = "3.0")
    double gewichtZeitverfall;

    @ConfigProperty(name = "zuteilung.zeitverfall.horizont-stunden", defaultValue = "24")
    double zeitverfallHorizontStunden;

    @ConfigProperty(name = "zuteilung.topologie.mindestgroesse", defaultValue = "3")
    int topologieMindestgroesse;

    @ConfigProperty(name = "zuteilung.suche.strahlbreite", defaultValue = "5000")
    int strahlbreite;

    @ConfigProperty(name = "zuteilung.vormerkung.sekunden", defaultValue = "30")
    long vormerkSekunden;

    /**
     * Sperrt den Wagen (SELECT ... FOR UPDATE): konkurrierende Buchungen desselben Wagens serialisieren,
     * ein eindeutiger Lock-Pfad pro Wagen verhindert Deadlocks.
     */
    private Wagen wagenSperren(String wagenId) {
        Wagen wagen = Wagen.findById(wagenId, LockModeType.PESSIMISTIC_WRITE);
        if (wagen == null) {
            throw new WagenNichtGefundenException(wagenId);
        }
        return wagen;
    }

    private Wagen wagenFinden(String wagenId) {
        Wagen wagen = Wagen.findById(wagenId);
        if (wagen == null) {
            throw new WagenNichtGefundenException(wagenId);
        }
        return wagen;
    }

    /**
     * Teilstrecke als Halte-Indizes [von, bis); ohne Angabe gilt die gesamte Strecke.
     */
    private int[] teilstrecke(Wagen wagen, Integer einstiegHalt, Integer ausstiegHalt) {
        int letzterHalt = wagen.halteNamen().size() - 1;
        int von = einstiegHalt != null ? einstiegHalt : 0;
        int bis = ausstiegHalt != null ? ausstiegHalt : letzterHalt;
        if (von < 0 || bis > letzterHalt || von >= bis) {
            throw new UngueltigeAnfrageException("Ungültige Teilstrecke: " + von + " -> " + bis);
        }
        return new int[] { von, bis };
    }

    /**
     * IDs der Sitze, die auf der Teilstrecke bereits (auch nur teilweise überlappend) reserviert sind.
     */
    private Set<String> belegteSitzIds(String wagenId, int von, int bis) {
        List<Sitzreservierung> ueberlappend = Sitzreservierung.list(
                "wagenId = ?1 and einstiegHalt < ?3 and ausstiegHalt > ?2", wagenId, von, bis);
        return ueberlappend.stream().map(r -> r.sitzId).collect(Collectors.toSet());
    }

    /**
     * IDs der Sitze, die auf der Teilstrecke von einer fremden, noch nicht abgelaufenen Vormerkung vorgemerkt sind.
     */
    private Set<String> vorgemerkteSitzIds(String wagenId, int von, int bis, String eigenerToken) {
        List<Vormerkung> vormerkungen = Vormerkung.list(
                "wagenId = ?1 and einstiegHalt < ?3 and ausstiegHalt > ?2 and ablaufzeitpunkt > ?4", wagenId, von, bis, Instant.now());
        return vormerkungen.stream()
                .filter(h -> !h.vormerkToken.equals(eigenerToken))
                .map(h -> h.sitzId)
                .collect(Collectors.toSet());
    }

    /**
     * Belegte und von anderen gehaltene Sitze; eigene Sperren (ownToken) zählen nicht.
     */
    private Set<String> nichtVerfuegbareSitzIds(String wagenId, int von, int bis, String eigenerToken) {
        Set<String> nichtVerfuegbar = new HashSet<>(belegteSitzIds(wagenId, von, bis));
        nichtVerfuegbar.addAll(vorgemerkteSitzIds(wagenId, von, bis, eigenerToken));
        return nichtVerfuegbar;
    }

    private void vormerkungenFreigeben(String wagenId, String vormerkToken) {
        Vormerkung.delete("wagenId = ?1 and ablaufzeitpunkt <= ?2", wagenId, Instant.now());
        if (vormerkToken != null) {
            Vormerkung.delete("vormerkToken", vormerkToken);
        }
    }

    private Instant vormerkungenAnlegen(String wagenId, String vormerkToken, Collection<String> sitzIds, int von, int bis) {
        Instant ablaufzeitpunkt = Instant.now().plusSeconds(vormerkSekunden);
        for (String sitzId : sitzIds) {
            Vormerkung h = new Vormerkung();
            h.vormerkToken = vormerkToken;
            h.wagenId = wagenId;
            h.sitzId = sitzId;
            h.einstiegHalt = von;
            h.ausstiegHalt = bis;
            h.ablaufzeitpunkt = ablaufzeitpunkt;
            h.persist();
        }
        return ablaufzeitpunkt;
    }

    private List<Gruppenkontingent> aktiveKontingente(String wagenId, Instant now) {
        List<Gruppenkontingent> kontingente = Gruppenkontingent.list("wagenId", wagenId);
        return kontingente.stream().filter(b -> b.istAktiv(now)).toList();
    }

    /**
     * Gruppen mit n >= 3 dürfen geschützte Kontingente nutzen, alle anderen nicht.
     */
    private Set<String> reserviertKontingentIds(List<Gruppenkontingent> aktiveKontingente, int gruppengroesse) {
        if (gruppengroesse >= 3) return Set.of();
        return aktiveKontingente.stream().map(b -> b.id).collect(Collectors.toSet());
    }

    /**
     * Anzahl der Personen: Sitzplätze und Rollstuhlplatz zählen, der Fahrradstellplatz nicht.
     */
    private int personen(Collection<Sitzplatz> plaetze) {
        return (int) plaetze.stream().filter(p -> p.art != Platzart.FAHRRAD).count();
    }

    private void anfragePruefen(GruppenreservierungRequest request) {
        if (request.wuenschtRuhezone() && request.mitKindern()) {
            throw new UngueltigeAnfrageException("Ruhezone und Kinderbereich schließen sich gegenseitig aus.");
        }
    }

    private List<Sitzreservierung> buchen(String wagenId, List<Sitzplatz> sitzplaetze, int von, int bis) {
        String reservierungId = UUID.randomUUID().toString();
        Instant erstelltAm = Instant.now();
        List<Sitzreservierung> zeilen = new ArrayList<>();

        for (Sitzplatz sitzplatz : sitzplaetze) {
            Sitzreservierung r = new Sitzreservierung();
            r.reservierungId = reservierungId;
            r.wagenId = wagenId;
            r.sitzId = sitzplatz.id;
            r.einstiegHalt = von;
            r.ausstiegHalt = bis;
            r.gruppengroesse = personen(sitzplaetze);
            r.erstelltAm = erstelltAm;
            r.persist();
            zeilen.add(r);
        }

        return zeilen;
    }

    @Transactional
    public Optional<Sitzplatz> einzelplatzZuteilen(String wagenId, EinzelreservierungRequest request) {
        Wagen wagen = wagenSperren(wagenId);
        int[] strecke = teilstrecke(wagen, request.einstiegHalt(), request.ausstiegHalt());

        Optional<Sitzplatz> sitzplatz = einzelplatzAuswaehlen(wagenId, request, nichtVerfuegbareSitzIds(wagenId, strecke[0], strecke[1], null));
        sitzplatz.ifPresent(s -> buchen(wagenId, List.of(s), strecke[0], strecke[1]));
        return sitzplatz;
    }

    @Transactional
    public List<Sitzplatz> gruppeZuteilen(String wagenId, GruppenreservierungRequest request) {
        anfragePruefen(request);
        Wagen wagen = wagenSperren(wagenId);
        int[] strecke = teilstrecke(wagen, request.einstiegHalt(), request.ausstiegHalt());

        Auswahl auswahl = auswaehlen(wagenId, request, nichtVerfuegbareSitzIds(wagenId, strecke[0], strecke[1], null));
        if (!auswahl.sitzplaetze().isEmpty()) {
            buchen(wagenId, auswahl.sitzplaetze(), strecke[0], strecke[1]);
        }
        return auswahl.sitzplaetze();
    }

    /**
     * Ergebnis der Platzwahl: Sonderplätze und Sitzplätze, oder leer mit Meldung.
     */
    private record Auswahl(List<Sitzplatz> sitzplaetze, String meldung) {
    }

    /**
     * Wählt die gewünschten Sonderplätze (Rollstuhl, Fahrrad) und die Sitzplätze der Gruppe. Der Rollstuhlplatz
     * ersetzt einen Sitzplatz, der Fahrradstellplatz kommt zusätzlich hinzu. Gibt es einen Wunsch nicht, wird nichts vergeben.
     */
    private Auswahl auswaehlen(String wagenId, GruppenreservierungRequest request, Set<String> nichtVerfuegbar) {
        List<Sitzplatz> ergebnis = new ArrayList<>();

        for (Platzart art : List.of(Platzart.ROLLSTUHL, Platzart.FAHRRAD)) {
            if (art == Platzart.ROLLSTUHL ? !request.wuenschtRollstuhlplatz() : !request.wuenschtFahrradplatz()) continue;

            List<Sitzplatz> sonderplaetze = Sitzplatz.list("wagenId = ?1 and art = ?2 order by reihe, spalte", wagenId, art);
            Optional<Sitzplatz> frei = sonderplaetze.stream().filter(p -> !nichtVerfuegbar.contains(p.id)).findFirst();
            if (frei.isEmpty()) {
                return new Auswahl(List.of(), (art == Platzart.ROLLSTUHL ? "Kein Rollstuhlplatz" : "Kein Fahrradstellplatz")
                        + " auf dieser Teilstrecke verfügbar.");
            }
            ergebnis.add(frei.get());
        }

        int sitze = request.gruppengroesse() - (request.wuenschtRollstuhlplatz() ? 1 : 0);
        if (sitze > 0) {
            List<Sitzplatz> gewaehlt = sitze == 1 && !request.mitKindern()
                    ? einzelplatzAuswaehlen(wagenId, new EinzelreservierungRequest(request.wuenschtSteckdose(), request.wuenschtFenster(),
                            request.wuenschtTisch(), request.wuenschtRuhezone(), request.einstiegHalt(), request.ausstiegHalt(),
                            request.anfragezeitpunkt()), nichtVerfuegbar).map(List::of).orElse(List.of())
                    : gruppeAuswaehlen(wagenId, request, sitze, nichtVerfuegbar);
            if (gewaehlt.isEmpty()) {
                return new Auswahl(List.of(), "Keine passende Sitzplatzgruppe verfügbar.");
            }
            ergebnis.addAll(gewaehlt);
        }

        return new Auswahl(ergebnis, null);
    }

    /**
     * Sitzplan der Teilstrecke mit Verfügbarkeit je Sitzplatz. Mit vorschlag wird der beste Vorschlag ermittelt
     * und für die Vormerkdauer vorgemerkt; die bisherige Vormerkung des Tokens wird dabei ersetzt.
     */
    @Transactional
    public SitzplanResponse sitzplanErstellen(String wagenId, GruppenreservierungRequest request, boolean vorschlag) {
        anfragePruefen(request);
        Wagen wagen = vorschlag ? wagenSperren(wagenId) : wagenFinden(wagenId);
        int[] strecke = teilstrecke(wagen, request.einstiegHalt(), request.ausstiegHalt());
        String token = request.vormerkToken();

        Auswahl auswahl = new Auswahl(List.of(), null);
        Instant ablaufzeitpunkt = null;
        if (vorschlag) {
            vormerkungenFreigeben(wagenId, token);
            auswahl = auswaehlen(wagenId, request, nichtVerfuegbareSitzIds(wagenId, strecke[0], strecke[1], token));

            if (!auswahl.sitzplaetze().isEmpty()) {
                token = token != null ? token : UUID.randomUUID().toString();
                ablaufzeitpunkt = vormerkungenAnlegen(wagenId, token, auswahl.sitzplaetze().stream().map(s -> s.id).toList(),
                        strecke[0], strecke[1]);
            }
        }

        return sitzplanAufbauen(wagenId, strecke, request, token,
                auswahl.sitzplaetze().stream().map(s -> s.id).toList(), auswahl.meldung(), ablaufzeitpunkt);
    }

    private SitzplanResponse sitzplanAufbauen(String wagenId, int[] strecke, GruppenreservierungRequest profil, String vormerkToken,
                                List<String> vorgeschlagen, String meldung, Instant vormerkungAblauf) {
        Set<String> belegt = belegteSitzIds(wagenId, strecke[0], strecke[1]);
        Set<String> vorgemerkt = vorgemerkteSitzIds(wagenId, strecke[0], strecke[1], vormerkToken);
        Set<String> reserviert = reserviertKontingentIds(aktiveKontingente(wagenId, profil.wirksamerZeitpunkt()), profil.gruppengroesse());

        List<SitzplatzAnsicht> ansichten = Sitzplatz.<Sitzplatz>find("wagenId = ?1 order by reihe, spalte", wagenId).list().stream()
                .map(s -> SitzplatzAnsicht.von(s, zustandVon(s, belegt, vorgemerkt, reserviert, profil)))
                .toList();

        return new SitzplanResponse(wagenId, strecke[0], strecke[1], ansichten, vorgeschlagen, meldung, vormerkToken, vormerkungAblauf);
    }

    private Sitzzustand zustandVon(Sitzplatz sitzplatz, Set<String> belegt, Set<String> vorgemerkt, Set<String> reserviert,
                                   GruppenreservierungRequest profil) {
        if (belegt.contains(sitzplatz.id)) return Sitzzustand.BELEGT;
        if (vorgemerkt.contains(sitzplatz.id)) return Sitzzustand.VORGEMERKT;
        if (sitzplatz.art != Platzart.SITZ) {
            boolean gewuenscht = sitzplatz.art == Platzart.ROLLSTUHL ? profil.wuenschtRollstuhlplatz() : profil.wuenschtFahrradplatz();
            return gewuenscht ? Sitzzustand.FREI : Sitzzustand.SONDERPLATZ;
        }
        if (sitzplatz.kontingentId != null && reserviert.contains(sitzplatz.kontingentId)) return Sitzzustand.GRUPPENKONTINGENT;
        if (profil.wuenschtRuhezone() && sitzplatz.zone != Zone.RUHE) return Sitzzustand.ANDERE_ZONE;
        if (profil.mitKindern() && sitzplatz.zone == Zone.RUHE) return Sitzzustand.ANDERE_ZONE;
        return Sitzzustand.FREI;
    }

    /**
     * Ersetzt die Vormerkung des Tokens durch die gewählten Sitzplätze (manuelles Umwählen); leere Liste gibt sie frei.
     */
    @Transactional
    public SitzplanResponse vormerken(String wagenId, VormerkRequest request) {
        Wagen wagen = wagenSperren(wagenId);
        int[] strecke = teilstrecke(wagen, request.einstiegHalt(), request.ausstiegHalt());
        String token = request.vormerkToken();

        vormerkungenFreigeben(wagenId, token);

        List<String> ids = request.sitzIds();
        Instant ablaufzeitpunkt = null;
        Set<Sitzplatz> gewaehlt = Set.of();
        if (!ids.isEmpty()) {
            gewaehlt = waehlbarPruefen(wagenId, ids, strecke, token, request.wirksamerZeitpunkt());
            token = token != null ? token : UUID.randomUUID().toString();
            ablaufzeitpunkt = vormerkungenAnlegen(wagenId, token, gewaehlt.stream().map(s -> s.id).toList(), strecke[0], strecke[1]);
        }

        GruppenreservierungRequest profil = new GruppenreservierungRequest(
                request.gruppengroesse() != null ? request.gruppengroesse() : Math.max(1, personen(gewaehlt)),
                false, false, false, request.wuenschtRuhezone(), request.wuenschtRollstuhlplatz(), request.wuenschtFahrradplatz(),
                request.mitKindern(), strecke[0], strecke[1], token, request.anfragezeitpunkt());
        return sitzplanAufbauen(wagenId, strecke, profil, token, ids, null, ablaufzeitpunkt);
    }

    /**
     * Prüft, dass die Sitze existieren und auf der Teilstrecke weder belegt, fremd vorgemerkt noch für Gruppen reserviert sind.
     */
    private Set<Sitzplatz> waehlbarPruefen(String wagenId, List<String> sitzIds, int[] strecke, String eigenerToken, Instant now) {
        Set<String> ids = new LinkedHashSet<>(sitzIds);
        if (ids.size() != sitzIds.size()) {
            throw new UngueltigeAnfrageException("Doppelte Sitze in der Anfrage.");
        }

        List<Sitzplatz> sitzplaetze = Sitzplatz.<Sitzplatz>find("wagenId = ?1 and id in ?2", wagenId, ids).list();
        if (sitzplaetze.size() != ids.size()) {
            throw new UngueltigeAnfrageException("Unbekannte Sitze für Wagen " + wagenId + ".");
        }

        Set<String> belegt = belegteSitzIds(wagenId, strecke[0], strecke[1]);
        Set<String> vorgemerkt = vorgemerkteSitzIds(wagenId, strecke[0], strecke[1], eigenerToken);
        Set<String> reserviert = reserviertKontingentIds(aktiveKontingente(wagenId, now), personen(sitzplaetze));

        for (Sitzplatz sitzplatz : sitzplaetze) {
            if (belegt.contains(sitzplatz.id)) {
                throw new ReservierungsKonfliktException("Sitz " + sitzplatz.id + " ist auf dieser Teilstrecke bereits vergeben.");
            }
            if (vorgemerkt.contains(sitzplatz.id)) {
                throw new ReservierungsKonfliktException("Sitz " + sitzplatz.id + " wird gerade von einer anderen Anfrage vorgemerkt.");
            }
            if (sitzplatz.kontingentId != null && reserviert.contains(sitzplatz.kontingentId)) {
                throw new ReservierungsKonfliktException("Sitz " + sitzplatz.id + " ist für Gruppen reserviert.");
            }
        }

        return new LinkedHashSet<>(sitzplaetze);
    }

    /**
     * Bucht die gewählten Sitze unter dem Wagen-Lock und prüft sie erneut gegen Teilstrecke, Vormerkungen und Kontingente.
     */
    @Transactional
    public ReservierungResponse bestaetigen(String wagenId, BestaetigungRequest request) {
        Wagen wagen = wagenSperren(wagenId);
        int[] strecke = teilstrecke(wagen, request.einstiegHalt(), request.ausstiegHalt());

        vormerkungenFreigeben(wagenId, request.vormerkToken());
        Set<Sitzplatz> sitzplaetze = waehlbarPruefen(wagenId, request.sitzIds(), strecke, request.vormerkToken(), request.wirksamerZeitpunkt());

        return alsResponse(buchen(wagenId, new ArrayList<>(sitzplaetze), strecke[0], strecke[1]));
    }

    @Transactional
    public List<ReservierungResponse> reservierungen(String wagenId) {
        wagenFinden(wagenId);

        List<Sitzreservierung> zeilen = Sitzreservierung.list("wagenId = ?1 order by erstelltAm, id", wagenId);
        Map<String, List<Sitzreservierung>> nachReservierung = new LinkedHashMap<>();
        for (Sitzreservierung r : zeilen) {
            nachReservierung.computeIfAbsent(r.reservierungId, k -> new ArrayList<>()).add(r);
        }

        return nachReservierung.values().stream().map(this::alsResponse).toList();
    }

    /**
     * Gibt alle Sitzplätze einer Reservierung frei.
     */
    @Transactional
    public long stornieren(String wagenId, String reservierungId) {
        wagenSperren(wagenId);
        long anzahl = Sitzreservierung.delete("wagenId = ?1 and reservierungId = ?2", wagenId, reservierungId);
        if (anzahl == 0) {
            throw new ReservierungNichtGefundenException(reservierungId);
        }
        return anzahl;
    }

    /**
     * Kennzahlen der Belegung: Auslastung der Sitzplätze je Teilstrecke und Anteil zusammenhängender Gruppen.
     */
    @Transactional
    public KennzahlenResponse kennzahlen(String wagenId) {
        Wagen wagen = wagenFinden(wagenId);
        Map<String, Sitzplatz> sitzplaetze = new HashMap<>();
        for (Sitzplatz s : Sitzplatz.<Sitzplatz>list("wagenId = ?1 and art = ?2", wagenId, Platzart.SITZ)) {
            sitzplaetze.put(s.id, s);
        }
        List<Sitzreservierung> zeilen = Sitzreservierung.list("wagenId", wagenId);

        int strecken = wagen.halteNamen().size() - 1;
        List<Double> auslastung = new ArrayList<>();
        for (int i = 0; i < strecken; i++) {
            int teilstrecke = i;
            long belegt = zeilen.stream()
                    .filter(z -> sitzplaetze.containsKey(z.sitzId) && z.einstiegHalt <= teilstrecke && z.ausstiegHalt > teilstrecke)
                    .count();
            auslastung.add(sitzplaetze.isEmpty() ? 0.0 : belegt * 100.0 / sitzplaetze.size());
        }

        Map<String, List<Sitzplatz>> nachReservierung = new LinkedHashMap<>();
        for (Sitzreservierung z : zeilen) {
            nachReservierung.computeIfAbsent(z.reservierungId, k -> new ArrayList<>());
            if (sitzplaetze.containsKey(z.sitzId)) nachReservierung.get(z.reservierungId).add(sitzplaetze.get(z.sitzId));
        }
        int gruppen = 0;
        int zusammenhaengend = 0;
        for (List<Sitzplatz> gruppe : nachReservierung.values()) {
            if (gruppe.size() < 2) continue;
            gruppen++;
            if (groessteKomponente(gruppe) == gruppe.size()) zusammenhaengend++;
        }

        double gesamt = auslastung.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new KennzahlenResponse(gesamt, auslastung, nachReservierung.size(), gruppen, zusammenhaengend);
    }

    @Transactional
    public long zuruecksetzen(String wagenId) {
        wagenSperren(wagenId);
        Vormerkung.delete("wagenId", wagenId);
        return Sitzreservierung.delete("wagenId", wagenId);
    }

    private ReservierungResponse alsResponse(List<Sitzreservierung> zeilen) {
        Sitzreservierung erste = zeilen.get(0);
        return new ReservierungResponse(erste.reservierungId, erste.wagenId, zeilen.stream().map(r -> r.sitzId).toList(),
                erste.einstiegHalt, erste.ausstiegHalt, erste.gruppengroesse, erste.erstelltAm);
    }

    private Optional<Sitzplatz> einzelplatzAuswaehlen(String wagenId, EinzelreservierungRequest request, Set<String> belegt) {
        Instant now = request.wirksamerZeitpunkt();

        // 1. Aktive Gruppenkontingente des Wagens laden; Einzelreisende dürfen sie nie nutzen
        List<Gruppenkontingent> aktiveKontingente = aktiveKontingente(wagenId, now);
        Set<String> reserviertKontingentIds = reserviertKontingentIds(aktiveKontingente, 1);

        // 2. Alle Sitze abfragen; belegte und vorgemerkte Sitze gelten als besetzt
        List<Sitzplatz> alleSitzplaetze = Sitzplatz.find("wagenId = ?1 and art = ?2 order by reihe, spalte", wagenId, Platzart.SITZ).list();
        List<Sitzplatz> belegteSitzplaetze = alleSitzplaetze.stream().filter(s -> belegt.contains(s.id)).toList();

        // 3. Freie, nicht kontingentierte Sitze (Stufe 1)
        List<Sitzplatz> ohneKontingent = alleSitzplaetze.stream()
                .filter(s -> !belegt.contains(s.id))
                .filter(s -> s.kontingentId == null || !reserviertKontingentIds.contains(s.kontingentId))
                .toList();

        // Topologieschutz: nur solange ein Kontingent aktiv ist und die größte freie Komponente noch >= K ist
        boolean topologieSchutz = !aktiveKontingente.isEmpty() && groessteKomponente(ohneKontingent) >= topologieMindestgroesse;

        // Zeitverfall: Tisch-Malus sinkt linear auf 0 bis zur nächsten Freigabefrist
        double zeitverfallAnteil = aktiveKontingente.stream()
                .map(b -> b.freigabefrist)
                .min(Comparator.naturalOrder())
                .map(frist -> Math.max(0.0, Math.min(1.0,
                        Duration.between(now, frist).toMillis() / (zeitverfallHorizontStunden * 3_600_000.0))))
                .orElse(0.0);

        // 4. Scoring ausführen (Stufe 2) je Zonenstufe
        for (Set<Zone> erlaubteZonen : zonenStufen(request.wuenschtRuhezone(), false)) {
            List<Sitzplatz> kandidaten = ohneKontingent.stream()
                    .filter(s -> erlaubteZonen.contains(s.zone))
                    .filter(s -> !topologieSchutz || groessteKomponente(ohne(ohneKontingent, s)) >= topologieMindestgroesse)
                    .toList();

            Sitzplatz besterSitzplatz = null;
            double hoechsterScore = -Double.MAX_VALUE;

            for (Sitzplatz sitzplatz : kandidaten) {
                double praeferenz = praeferenzScore(sitzplatz, request);
                double streuung = streuungsScore(sitzplatz, belegteSitzplaetze);
                double gesamtScore = (gewichtPraeferenz * praeferenz) + (gewichtStreuung * streuung);
                if (sitzplatz.hatTisch) gesamtScore -= gewichtZeitverfall * zeitverfallAnteil;

                if (gesamtScore > hoechsterScore) {
                    hoechsterScore = gesamtScore;
                    besterSitzplatz = sitzplatz;
                }
            }

            if (besterSitzplatz != null) {
                return Optional.of(besterSitzplatz);
            }
        }

        return Optional.empty();
    }

    /**
     * Zonenstufen der Suche. Ruhewunsch: nur Ruhezone (hart). Familie mit Kindern: erst nur Kinderbereich, dann zusätzlich Standard (mit Bonus für Kinderplätze), nie Ruhezone.
     * Sonst: Standard, danach zusätzlich Ruhezone, zuletzt zusätzlich Kinderbereich als Fallback.
     */
    private List<Set<Zone>> zonenStufen(boolean wuenschtRuhezone, boolean mitKindern) {
        if (wuenschtRuhezone) return List.of(EnumSet.of(Zone.RUHE));
        if (mitKindern) return List.of(EnumSet.of(Zone.KINDER), EnumSet.of(Zone.STANDARD, Zone.KINDER));
        return List.of(EnumSet.of(Zone.STANDARD), EnumSet.of(Zone.STANDARD, Zone.RUHE), EnumSet.allOf(Zone.class));
    }

    private List<Sitzplatz> ohne(List<Sitzplatz> sitzplaetze, Sitzplatz ausgeschlossen) {
        return sitzplaetze.stream().filter(s -> s != ausgeschlossen).toList();
    }

    /**
     * Größe der größten zusammenhängenden Komponente der übergebenen Sitze (BFS über Adjazenz).
     */
    private int groessteKomponente(List<Sitzplatz> sitzplaetze) {
        Set<Sitzplatz> unbesucht = new LinkedHashSet<>(sitzplaetze);
        int groesste = 0;

        while (!unbesucht.isEmpty()) {
            Deque<Sitzplatz> warteschlange = new ArrayDeque<>();
            Sitzplatz start = unbesucht.iterator().next();
            unbesucht.remove(start);
            warteschlange.add(start);
            int size = 0;

            while (!warteschlange.isEmpty()) {
                Sitzplatz aktuell = warteschlange.poll();
                size++;
                Iterator<Sitzplatz> it = unbesucht.iterator();
                while (it.hasNext()) {
                    Sitzplatz anderer = it.next();
                    if (nachbarschaftsabstand(aktuell, anderer) <= 1.85) {
                        it.remove();
                        warteschlange.add(anderer);
                    }
                }
            }
            groesste = Math.max(groesste, size);
        }

        return groesste;
    }

    private double praeferenzScore(Sitzplatz sitzplatz, EinzelreservierungRequest request) {
        double score = 0.0;
        if (request.wuenschtSteckdose() && sitzplatz.hatSteckdose) score += 1.0;
        if (request.wuenschtFenster() && sitzplatz.amFenster) score += 1.0;
        if (request.wuenschtTisch() && sitzplatz.hatTisch) score += 1.5;
        return score;
    }

    private double streuungsScore(Sitzplatz kandidat, List<Sitzplatz> belegteSitzplaetze) {
        if (belegteSitzplaetze.isEmpty()) {
            return 10.0; // Standardwert bei leerem Wagen
        }

        double minAbstand = Double.MAX_VALUE;
        for (Sitzplatz belegte : belegteSitzplaetze) {
            // Reihenabstand gewichtet mit 1.5, Spaltenabstand 1.0
            double abstand = Math.sqrt(
                    Math.pow((kandidat.reihe - belegte.reihe) * 1.5, 2) +
                            Math.pow(kandidat.spalte - belegte.spalte, 2)
            );
            if (abstand < minAbstand) {
                minAbstand = abstand;
            }
        }
        return minAbstand;
    }

    private List<Sitzplatz> gruppeAuswaehlen(String wagenId, GruppenreservierungRequest request, int n, Set<String> belegt) {
        Instant now = request.wirksamerZeitpunkt();

        // 1. Reservierte Kontingente identifizieren (Gruppen mit mindestens 3 Personen dürfen sie nutzen)
        Set<String> reserviertKontingentIds = reserviertKontingentIds(aktiveKontingente(wagenId, now), request.gruppengroesse());

        // 2. Verfügbare Sitze filtern
        List<Sitzplatz> alleSitzplaetze = Sitzplatz.find("wagenId = ?1 and art = ?2 order by reihe, spalte", wagenId, Platzart.SITZ).list();
        List<Sitzplatz> belegteSitzplaetze = alleSitzplaetze.stream().filter(s -> belegt.contains(s.id)).toList();

        // Zonenstufen: Ruhewunsch hart, Familien im Kinderbereich, sonst Standard mit Fallbacks
        for (Set<Zone> erlaubteZonen : zonenStufen(request.wuenschtRuhezone(), request.mitKindern())) {
            List<Sitzplatz> kandidaten = alleSitzplaetze.stream()
                    .filter(s -> !belegt.contains(s.id))
                    .filter(s -> s.kontingentId == null || !reserviertKontingentIds.contains(s.kontingentId))
                    .filter(s -> erlaubteZonen.contains(s.zone))
                    .sorted(Comparator.comparingInt((Sitzplatz s) -> s.reihe).thenComparingInt(s -> s.spalte))
                    .toList();

            if (kandidaten.size() >= n) {
                // 3. Zusammenhängende Sitzgruppen der Größe n generieren
                List<List<Sitzplatz>> sitzgruppen = zusammenhaengendeSitzgruppen(kandidaten, n);

                // 4. Sitzgruppen bewerten (Score maximieren)
                List<Sitzplatz> besteSitzgruppe = null;
                double hoechsterScore = -Double.MAX_VALUE;

                for (List<Sitzplatz> sitzgruppe : sitzgruppen) {
                    double praeferenz = gruppenPraeferenzScore(sitzgruppe, request);
                    double kohaesion = kohaesionsScore(sitzgruppe);
                    double streuung = gruppenStreuungsScore(sitzgruppe, belegteSitzplaetze);

                    double gesamtScore = (gewichtPraeferenz * praeferenz)
                            + (gewichtKohaesion * kohaesion)
                            + (gewichtStreuung * streuung);
                    if (request.mitKindern()) gesamtScore += gewichtKinderbereich * kinderAnteil(sitzgruppe);

                    if (gesamtScore > hoechsterScore) {
                        hoechsterScore = gesamtScore;
                        besteSitzgruppe = sitzgruppe;
                    }
                }

                if (besteSitzgruppe != null) {
                    return besteSitzgruppe;
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Findet zusammenhängende Sitzmengen der Größe n durch schrittweises Wachsen.
     * Jede Stufe enthält jede Sitzmenge nur einmal (Schlüssel: sortierte Sitz-IDs).
     * Übersteigt eine Stufe die Strahlbreite, bleiben nur die kompaktesten Teilmengen (Heuristik).
     */
    private List<List<Sitzplatz>> zusammenhaengendeSitzgruppen(List<Sitzplatz> verfuegbar, int zielgroesse) {
        Map<String, List<Sitzplatz>> stufe = new LinkedHashMap<>();
        for (Sitzplatz start : verfuegbar) {
            stufe.put(start.id, new ArrayList<>(List.of(start)));
        }

        for (int size = 1; size < zielgroesse; size++) {
            Map<String, List<Sitzplatz>> naechste = new LinkedHashMap<>();
            for (List<Sitzplatz> sitzgruppe : stufe.values()) {
                for (Sitzplatz kandidat : verfuegbar) {
                    if (sitzgruppe.contains(kandidat)) continue;

                    // Kandidat muss direkt an mindestens einen Sitz der Sitzgruppe grenzen
                    if (sitzgruppe.stream().noneMatch(s -> nachbarschaftsabstand(s, kandidat) <= 1.85)) continue;

                    List<Sitzplatz> erweitert = new ArrayList<>(sitzgruppe);
                    erweitert.add(kandidat);
                    String schluessel = erweitert.stream().map(s -> s.id).sorted().collect(Collectors.joining(","));
                    naechste.putIfAbsent(schluessel, erweitert);
                }
            }
            if (naechste.size() > strahlbreite) {
                Map<String, List<Sitzplatz>> beschnitten = new LinkedHashMap<>();
                naechste.entrySet().stream()
                        .map(e -> Map.entry(kohaesionsScore(e.getValue()), e))
                        .sorted((a, b) -> Double.compare(b.getKey(), a.getKey()))
                        .limit(strahlbreite)
                        .forEach(e -> beschnitten.put(e.getValue().getKey(), e.getValue().getValue()));
                naechste = beschnitten;
            }
            stufe = naechste;
        }

        return new ArrayList<>(stufe.values());
    }

    /**
     * Metrik für geometrische Nähe im Zugwaggon (2+2 Layout).
     */
    private double nachbarschaftsabstand(Sitzplatz a, Sitzplatz b) {
        int reihenDifferenz = Math.abs(a.reihe - b.reihe);
        int spaltenDifferenz = Math.abs(a.spalte - b.spalte);

        // Gleiche Reihe, nebeneinander auf derselben Gangseite (z.B. A-B oder C-D)
        if (reihenDifferenz == 0 && spaltenDifferenz == 1 && ((a.spalte == 0 && b.spalte == 1) || (a.spalte == 2 && b.spalte == 3)
                || (a.spalte == 1 && b.spalte == 0) || (a.spalte == 3 && b.spalte == 2))) {
            return 1.0;
        }

        // Gleiche Reihe über den Mittelgang hinweg (B-C)
        if (reihenDifferenz == 0 && spaltenDifferenz == 1) {
            return 1.8;
        }

        // Direkt hintereinander / gegenüber
        if (reihenDifferenz == 1 && spaltenDifferenz == 0) {
            // Gegenübersitz am Tisch: Tischreihen bilden Paare (2k, 2k+1), nur diese sitzen sich gegenüber
            if (a.hatTisch && b.hatTisch && a.reihe / 2 == b.reihe / 2) return 1.0;
            return 1.2;
        }

        // Diagonal hintereinander
        if (reihenDifferenz == 1 && spaltenDifferenz == 1) {
            return 1.7;
        }

        return 5.0; // Nicht zusammenhängend
    }

    /**
     * Misst die Kompaktheit der Gruppe: Je kleiner die paarweisen Abstände, desto höher der Score.
     */
    private double kohaesionsScore(List<Sitzplatz> sitzgruppe) {
        if (sitzgruppe.size() <= 1) return 10.0;

        double abstandSumme = 0.0;
        int paare = 0;

        for (int i = 0; i < sitzgruppe.size(); i++) {
            for (int j = i + 1; j < sitzgruppe.size(); j++) {
                abstandSumme += nachbarschaftsabstand(sitzgruppe.get(i), sitzgruppe.get(j));
                paare++;
            }
        }
        double abstandMittel = abstandSumme / paare;
        return 10.0 / (1.0 + abstandMittel); // Invertierte Distanz als Bonus
    }

    /**
     * Anteil der Sitzplätze der Gruppe im Kinderbereich (0 bis 1).
     */
    private double kinderAnteil(List<Sitzplatz> sitzgruppe) {
        return sitzgruppe.stream().filter(s -> s.zone == Zone.KINDER).count() / (double) sitzgruppe.size();
    }

    private double gruppenPraeferenzScore(List<Sitzplatz> sitzgruppe, GruppenreservierungRequest request) {
        double score = 0.0;
        for (Sitzplatz s : sitzgruppe) {
            if (request.wuenschtSteckdose() && s.hatSteckdose) score += 1.0;
            if (request.wuenschtFenster() && s.amFenster) score += 1.0;
            if (request.wuenschtTisch() && s.hatTisch) score += 1.5; // Tische werden für Gruppen höher belohnt
        }
        return score / sitzgruppe.size(); // Normalisiert pro Platz
    }

    private double gruppenStreuungsScore(List<Sitzplatz> sitzgruppe, List<Sitzplatz> belegte) {
        if (belegte.isEmpty()) return 10.0;

        double minAbstand = Double.MAX_VALUE;
        for (Sitzplatz c : sitzgruppe) {
            for (Sitzplatz o : belegte) {
                double d = Math.sqrt(Math.pow((c.reihe - o.reihe) * 1.5, 2) + Math.pow(c.spalte - o.spalte, 2));
                if (d < minAbstand) minAbstand = d;
            }
        }
        return minAbstand;
    }
}
