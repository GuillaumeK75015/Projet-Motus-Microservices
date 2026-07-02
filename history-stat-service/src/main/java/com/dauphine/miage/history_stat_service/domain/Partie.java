package com.dauphine.miage.history_stat_service.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "parties")
public class Partie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long joueurId;

    @Column(nullable = false)
    private String motSecret;

    @Column(nullable = false)
    private int nombreTentatives;

    @Column(nullable = false)
    private boolean gagne;

    private LocalDateTime dateFin;

    public Partie() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getJoueurId() { return joueurId; }
    public void setJoueurId(Long joueurId) { this.joueurId = joueurId; }

    public String getMotSecret() { return motSecret; }
    public void setMotSecret(String motSecret) { this.motSecret = motSecret; }

    public int getNombreTentatives() { return nombreTentatives; }
    public void setNombreTentatives(int nombreTentatives) { this.nombreTentatives = nombreTentatives; }

    public boolean isGagne() { return gagne; }
    public void setGagne(boolean gagne) { this.gagne = gagne; }

    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }
}
