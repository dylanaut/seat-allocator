package org.example.train.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "sitzplaetze")
public class Sitzplatz extends PanacheEntityBase {

    @Id
    @Column(name = "sitz_id", length = 10)
    public String id;

    @Column(name = "wagen_id", nullable = false, length = 20)
    public String wagenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "art", nullable = false)
    public Platzart art = Platzart.SITZ;

    @Column(name = "reihe", nullable = false)
    public int reihe;

    @Column(name = "spalte", nullable = false)
    public int spalte;

    @Column(name = "hat_steckdose", nullable = false)
    public boolean hatSteckdose;

    @Column(name = "am_fenster", nullable = false)
    public boolean amFenster;

    @Column(name = "hat_tisch", nullable = false)
    public boolean hatTisch;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false)
    public Zone zone = Zone.STANDARD;

    @Column(name = "kontingent_id")
    public String kontingentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kontingent_id", insertable = false, updatable = false)
    public Gruppenkontingent kontingent;
}
