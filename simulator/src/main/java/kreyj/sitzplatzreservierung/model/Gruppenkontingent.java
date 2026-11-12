package kreyj.sitzplatzreservierung.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "gruppenkontingente")
public class Gruppenkontingent extends PanacheEntityBase {

    @Id
    @Column(name = "kontingent_id", nullable = false)
    public String id;

    @Column(name = "wagen_id", nullable = false, length = 80)
    public String wagenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wagen_id", insertable = false, updatable = false)
    public Wagen wagen;

    @Column(name = "freigabefrist", nullable = false)
    public Instant freigabefrist;

    public boolean istAktiv(Instant zeitpunkt) {
        return zeitpunkt.isBefore(freigabefrist);
    }
}
