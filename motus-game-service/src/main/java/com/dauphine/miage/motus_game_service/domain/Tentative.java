package com.dauphine.miage.motus_game_service.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "tentatives")
public class Tentative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "jeu_id", nullable = false)
    @JsonIgnore
    private Jeu jeu;

    @Column(nullable = false)
    private int numero;

    @Column(nullable = false)
    private String motPropose;

    // Résultat lettre par lettre, séparé par des virgules : BIEN_PLACE,ABSENT,MAL_PLACE,...
    @Column(nullable = false)
    private String feedback;

    public Tentative() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Jeu getJeu() { return jeu; }
    public void setJeu(Jeu jeu) { this.jeu = jeu; }

    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }

    public String getMotPropose() { return motPropose; }
    public void setMotPropose(String motPropose) { this.motPropose = motPropose; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
}
