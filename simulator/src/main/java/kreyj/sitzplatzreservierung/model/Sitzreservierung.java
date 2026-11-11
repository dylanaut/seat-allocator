package kreyj.sitzplatzreservierung.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Belegung eines Sitzplatzes auf der Teilstrecke [einstiegHalt, ausstiegHalt) (Halte-Indizes, ausstiegHalt exklusiv).
 */
@Entity
@Table(name = "sitzreservierungen")
public class Sitzreservierung extends PanacheEntity {

    @Column(name = "reservierung_id", nullable = false, length = 36)
    public String reservierungId;

    @Column(name = "wagen_id", nullable = false, length = 20)
    public String wagenId;

    @Column(name = "sitz_id", nullable = false, length = 10)
    public String sitzId;

    @Column(name = "einstieg_halt", nullable = false)
    public int einstiegHalt;

    @Column(name = "ausstieg_halt", nullable = false)
    public int ausstiegHalt;

    @Column(name = "gruppengroesse", nullable = false)
    public int gruppengroesse;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;
}
