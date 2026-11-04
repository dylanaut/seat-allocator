package org.example.train.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "wagen")
public class Wagen extends PanacheEntityBase {

    @Id
    @Column(name = "wagen_id", length = 20)
    public String id;

    @Column(name = "zugnummer", nullable = false)
    public String zugnummer;

    /**
     * Halte der Strecke in Fahrtrichtung, kommasepariert. Ohne Angabe gilt eine Strecke Start-Ziel.
     */
    @Column(name = "halte")
    public String halte;

    public List<String> halteNamen() {
        if (halte == null || halte.isBlank()) {
            return List.of("Start", "Ziel");
        }
        return Arrays.stream(halte.split(",")).map(String::trim).toList();
    }
}
