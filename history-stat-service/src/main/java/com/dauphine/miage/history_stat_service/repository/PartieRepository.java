package com.dauphine.miage.history_stat_service.repository;

import com.dauphine.miage.history_stat_service.domain.Partie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartieRepository extends JpaRepository<Partie, Long> {
    List<Partie> findByJoueurId(Long joueurId);
    void deleteByJoueurId(Long joueurId);
}
