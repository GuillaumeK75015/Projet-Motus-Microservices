package com.dauphine.miage.motus_game_service.dto;

public class LettreResultat {

    private String lettre;
    private String resultat; // BIEN_PLACE | MAL_PLACE | ABSENT

    public LettreResultat(String lettre, String resultat) {
        this.lettre  = lettre;
        this.resultat = resultat;
    }

    public String getLettre()   { return lettre; }
    public String getResultat() { return resultat; }
}
