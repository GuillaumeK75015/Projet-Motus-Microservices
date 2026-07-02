package com.dauphine.miage.motus_game_service.dto;

public class StartGameRequest {

    private Long joueurId;

    /**
     * Nombre de lettres du mot à trouver. Doit être compris entre 7 et 10 inclus.
     * Conformément aux règles du jeu Motus (règle en vigueur depuis 2010).
     * Si absent, 7 est utilisé par défaut.
     */
    private Integer nombreLettres;

    public Long getJoueurId()                  { return joueurId; }
    public void setJoueurId(Long joueurId)      { this.joueurId = joueurId; }

    public Integer getNombreLettres()           { return nombreLettres; }
    public void setNombreLettres(Integer v)     { this.nombreLettres = v; }
}
