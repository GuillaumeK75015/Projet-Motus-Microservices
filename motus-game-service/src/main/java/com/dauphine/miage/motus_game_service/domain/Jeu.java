package com.dauphine.miage.motus_game_service.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "jeux",
    indexes = {
        @Index(name = "idx_jeux_joueur_id", columnList = "joueurId"),
        @Index(name = "idx_jeux_statut",    columnList = "statut")
    }
)
public class Jeu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long joueurId;

    @Column(nullable = false)
    private String motSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutJeu statut = StatutJeu.EN_COURS;

    private int tentativesMax = 6;

    private LocalDateTime dateDebut;

    // LAZY : les tentatives ne sont chargées que lorsqu'elles sont explicitement
    // accédées dans une transaction (via JOIN FETCH dans le repository).
    @OneToMany(mappedBy = "jeu", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("numero ASC")
    private List<Tentative> tentatives = new ArrayList<>();

    public Jeu() {}

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }

    public Long getJoueurId()                    { return joueurId; }
    public void setJoueurId(Long joueurId)       { this.joueurId = joueurId; }

    public String getMotSecret()                 { return motSecret; }
    public void setMotSecret(String motSecret)   { this.motSecret = motSecret; }

    public StatutJeu getStatut()                 { return statut; }
    public void setStatut(StatutJeu statut)      { this.statut = statut; }

    public int getTentativesMax()                { return tentativesMax; }
    public void setTentativesMax(int v)          { this.tentativesMax = v; }

    public LocalDateTime getDateDebut()          { return dateDebut; }
    public void setDateDebut(LocalDateTime v)    { this.dateDebut = v; }

    public List<Tentative> getTentatives()       { return tentatives; }
    public void setTentatives(List<Tentative> t) { this.tentatives = t; }
}
