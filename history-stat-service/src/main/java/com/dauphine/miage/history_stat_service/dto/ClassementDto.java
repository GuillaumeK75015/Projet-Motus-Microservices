package com.dauphine.miage.history_stat_service.dto;

public class ClassementDto {

    private Long joueurId;
    private int partiesJouees;
    private int partiesGagnees;
    private double tauxVictoire;
    private int rang;

    public ClassementDto(Long joueurId, int partiesJouees, int partiesGagnees, double tauxVictoire) {
        this.joueurId = joueurId;
        this.partiesJouees = partiesJouees;
        this.partiesGagnees = partiesGagnees;
        this.tauxVictoire = tauxVictoire;
    }

    public Long getJoueurId() { return joueurId; }
    public int getPartiesJouees() { return partiesJouees; }
    public int getPartiesGagnees() { return partiesGagnees; }
    public double getTauxVictoire() { return tauxVictoire; }
    public int getRang() { return rang; }
    public void setRang(int rang) { this.rang = rang; }
}
