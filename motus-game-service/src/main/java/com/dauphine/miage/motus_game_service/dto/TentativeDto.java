package com.dauphine.miage.motus_game_service.dto;

import java.util.List;

public class TentativeDto {

    private int numero;
    private String motPropose;
    private List<LettreResultat> lettres;

    public TentativeDto(int numero, String motPropose, List<LettreResultat> lettres) {
        this.numero     = numero;
        this.motPropose = motPropose;
        this.lettres    = lettres;
    }

    public int getNumero()              { return numero; }
    public String getMotPropose()       { return motPropose; }
    public List<LettreResultat> getLettres() { return lettres; }
}
