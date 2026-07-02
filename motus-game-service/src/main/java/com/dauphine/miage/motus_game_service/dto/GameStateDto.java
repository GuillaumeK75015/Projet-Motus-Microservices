package com.dauphine.miage.motus_game_service.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vue publique d'une partie retournée par l'API.
 * Le mot secret n'est révélé qu'à la fin de la partie.
 */
public class GameStateDto {

    private Long id;
    private Long joueurId;
    private String statut;
    private String message;

    // Indices toujours visibles
    private String premiereLettre;
    private int longueurMot;

    // Compteurs
    private int tentativesMax;
    private int tentativesEffectuees;
    private int tentativesRestantes;

    // Mot secret : null si EN_COURS, révélé si GAGNE ou PERDU
    private String motSecret;

    private LocalDateTime dateDebut;
    private List<TentativeDto> tentatives;

    public GameStateDto() {}

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId()                  { return id; }
    public void setId(Long id)           { this.id = id; }

    public Long getJoueurId()            { return joueurId; }
    public void setJoueurId(Long v)      { this.joueurId = v; }

    public String getStatut()            { return statut; }
    public void setStatut(String v)      { this.statut = v; }

    public String getMessage()           { return message; }
    public void setMessage(String v)     { this.message = v; }

    public String getPremiereLettre()    { return premiereLettre; }
    public void setPremiereLettre(String v) { this.premiereLettre = v; }

    public int getLongueurMot()          { return longueurMot; }
    public void setLongueurMot(int v)    { this.longueurMot = v; }

    public int getTentativesMax()        { return tentativesMax; }
    public void setTentativesMax(int v)  { this.tentativesMax = v; }

    public int getTentativesEffectuees()     { return tentativesEffectuees; }
    public void setTentativesEffectuees(int v) { this.tentativesEffectuees = v; }

    public int getTentativesRestantes()      { return tentativesRestantes; }
    public void setTentativesRestantes(int v)  { this.tentativesRestantes = v; }

    public String getMotSecret()         { return motSecret; }
    public void setMotSecret(String v)   { this.motSecret = v; }

    public LocalDateTime getDateDebut()  { return dateDebut; }
    public void setDateDebut(LocalDateTime v) { this.dateDebut = v; }

    public List<TentativeDto> getTentatives() { return tentatives; }
    public void setTentatives(List<TentativeDto> v) { this.tentatives = v; }
}
