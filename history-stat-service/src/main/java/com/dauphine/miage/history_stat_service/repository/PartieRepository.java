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
     *
     * Le test "IS NULL" de chaque paramètre optionnel est explicitement casté : sans ça,
     * PostgreSQL ne peut pas déterminer le type d'un paramètre qui n'apparaît que dans un
     * "? IS NULL" sans autre contexte typé (erreur "could not determine data type of
     * parameter", SQLState 42P18) — H2 (utilisé par le test @DataJpaTest) est plus permissif
     * et masque ce problème, mais la requête plante à 100% en base réelle sans ce cast.
     * Le second usage de chaque paramètre (la comparaison, ex. {@code p.gagne = :gagne})
     * reste volontairement non casté : Hibernate y infère déjà correctement le type depuis
     * l'attribut de l'entité, et caster aussi cette occurrence casse cette inférence pour
     * les paramètres non-numériques (ex. "cannot cast type bytea to boolean" pour :gagne).
     */
    @Query("""
        SELECT p FROM Partie p
        WHERE (CAST(:joueurId AS long)     IS NULL OR p.joueurId = :joueurId)
          AND (CAST(:gagne    AS boolean)  IS NULL OR p.gagne = :gagne)
          AND (CAST(:debut    AS timestamp) IS NULL OR p.dateFin >= :debut)
          AND (CAST(:fin      AS timestamp) IS NULL OR p.dateFin <  :fin)
        ORDER BY p.dateFin DESC
        """)
    List<Partie> search(@Param("joueurId") Long joueurId,
                        @Param("gagne")    Boolean gagne,
                        @Param("debut")    LocalDateTime debut,
                        @Param("fin")      LocalDateTime fin);

    /**
     * Historique filtré d'un seul joueur (date / gagné-perdu / mot), utilisé par l'écran
     * "Mon historique" côté joueur — contrairement à {@link #search}, joueurId n'est jamais
     * optionnel ici : un joueur ne doit voir que ses propres parties.
     * Le filtre "mot" est une recherche insensible à la casse sur une sous-chaîne du mot secret.
     * Comme pour les autres filtres, :mot est explicitement casté (y compris dans le CONCAT) :
     * sans ça, PostgreSQL ne sait pas déterminer son type ("function upper(bytea) does not
     * exist") alors que H2 laisse passer la requête sans broncher.
     */
    @Query("""
        SELECT p FROM Partie p
        WHERE p.joueurId = :joueurId
          AND (CAST(:gagne AS boolean)   IS NULL OR p.gagne = :gagne)
          AND (CAST(:debut AS timestamp) IS NULL OR p.dateFin >= :debut)
          AND (CAST(:fin   AS timestamp) IS NULL OR p.dateFin <  :fin)
          AND (CAST(:mot AS string) IS NULL OR UPPER(p.motSecret) LIKE UPPER(CONCAT('%', CAST(:mot AS string), '%')))
        ORDER BY p.dateFin DESC
        """)
    List<Partie> searchByJoueurId(@Param("joueurId") Long joueurId,
                                   @Param("gagne")    Boolean gagne,
                                   @Param("debut")    LocalDateTime debut,
                                   @Param("fin")      LocalDateTime fin,
                                   @Param("mot")      String mot);

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
