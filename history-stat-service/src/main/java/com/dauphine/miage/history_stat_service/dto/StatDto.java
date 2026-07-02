package com.dauphine.miage.history_stat_service.dto;

public class StatDto {

    private Long joueurId;
    private int partiesJouees;
    private int partiesGagnees;
    private double tauxVictoire;
    private double moyenneTentatives;

    public StatDto(Long joueurId, int partiesJouees, int partiesGagnees,
                   double tauxVictoire, double moyenneTentatives) {
        this.joueurId = joueurId;
        this.partiesJouees = partiesJouees;
        this.partiesGagnees = partiesGagnees;
        this.tauxVictoire = tauxVictoire;
        this.moyenneTentatives = moyenneTentatives;
    }

    public Long getJoueurId() { return joueurId; }
    public int getPartiesJouees() { return partiesJouees; }
    public int getPartiesGagnees() { return partiesGagnees; }
    public double getTauxVictoire() { return tauxVictoire; }
    public double getMoyenneTentatives() { return moyenneTentatives; }
}
