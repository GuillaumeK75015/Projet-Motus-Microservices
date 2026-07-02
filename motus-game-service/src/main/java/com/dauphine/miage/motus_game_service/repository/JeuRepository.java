package com.dauphine.miage.motus_game_service.repository;

import com.dauphine.miage.motus_game_service.domain.Jeu;
import com.dauphine.miage.motus_game_service.domain.StatutJeu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JeuRepository extends JpaRepository<Jeu, Long> {

    // JOIN FETCH évite le problème N+1 : tentatives chargées en une seule requête SQL
    @Query("SELECT DISTINCT j FROM Jeu j LEFT JOIN FETCH j.tentatives WHERE j.id = :id")
    Optional<Jeu> findByIdWithTentatives(@Param("id") Long id);

    @Query("SELECT DISTINCT j FROM Jeu j LEFT JOIN FETCH j.tentatives WHERE j.joueurId = :joueurId ORDER BY j.dateDebut DESC")
    List<Jeu> findByJoueurIdWithTentatives(@Param("joueurId") Long joueurId);

    @Query("SELECT DISTINCT j FROM Jeu j LEFT JOIN FETCH j.tentatives ORDER BY j.dateDebut DESC")
    List<Jeu> findAllWithTentatives();

    Optional<Jeu> findByJoueurIdAndStatut(Long joueurId, StatutJeu statut);
}
