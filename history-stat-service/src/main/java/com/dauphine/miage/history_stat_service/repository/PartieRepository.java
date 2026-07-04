package com.dauphine.miage.history_stat_service.repository;

import com.dauphine.miage.history_stat_service.domain.Partie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PartieRepository extends JpaRepository<Partie, Long> {

    List<Partie> findByJoueurId(Long joueurId);

    void deleteByJoueurId(Long joueurId);

    /**
     * Recherche multi-critères poussée en base (tous les paramètres sont optionnels).
     * Chaque filtre est ignoré quand son paramètre vaut {@code null}, ce qui évite de
     * charger toutes les parties en mémoire pour les filtrer côté application.
     * Le filtre "date" est traduit par le contrôleur en intervalle [debut, fin[ d'une journée.
     */
    @Query("""
        SELECT p FROM Partie p
        WHERE (:joueurId IS NULL OR p.joueurId = :joueurId)
          AND (:gagne    IS NULL OR p.gagne = :gagne)
          AND (:debut    IS NULL OR p.dateFin >= :debut)
          AND (:fin      IS NULL OR p.dateFin <  :fin)
        ORDER BY p.dateFin DESC
        """)
    List<Partie> search(@Param("joueurId") Long joueurId,
                        @Param("gagne")    Boolean gagne,
                        @Param("debut")    LocalDateTime debut,
                        @Param("fin")      LocalDateTime fin);

    /**
     * Agrégat du classement : une seule ligne par joueur (parties jouées / gagnées),
     * calculé côté base plutôt qu'en chargeant toutes les parties et en les groupant en mémoire.
     */
    @Query("""
        SELECT p.joueurId AS joueurId,
               COUNT(p) AS jouees,
               SUM(CASE WHEN p.gagne = true THEN 1 ELSE 0 END) AS gagnees
        FROM Partie p
        GROUP BY p.joueurId
        """)
    List<ClassementRow> aggregateClassement();

    /** Projection en lecture seule pour {@link #aggregateClassement()}. */
    interface ClassementRow {
        Long getJoueurId();
        long getJouees();
        long getGagnees();
    }
}
