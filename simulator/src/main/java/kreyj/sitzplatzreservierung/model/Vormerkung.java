package kreyj.sitzplatzreservierung.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Temporäre Vormerkung eines Sitzplatzes auf der Teilstrecke [einstiegHalt, ausstiegHalt) bis zum Ablaufzeitpunkt.
 */
@Entity
@Table(name = "vormerkungen")
public class Vormerkung extends PanacheEntity {

    @Column(name = "vormerk_token", nullable = false, length = 36)
    public String vormerkToken;

    @Column(name = "wagen_id", nullable = false, length = 80)
    public String wagenId;

    @Column(name = "sitz_id", nullable = false, length = 100)
    public String sitzId;

    @Column(name = "einstieg_halt", nullable = false)
    public int einstiegHalt;

    @Column(name = "ausstieg_halt", nullable = false)
    public int ausstiegHalt;

    @Column(name = "ablaufzeitpunkt", nullable = false)
    public Instant ablaufzeitpunkt;
}
