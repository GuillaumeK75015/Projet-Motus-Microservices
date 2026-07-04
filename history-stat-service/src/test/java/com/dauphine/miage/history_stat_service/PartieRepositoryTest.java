package com.dauphine.miage.history_stat_service;

import com.dauphine.miage.history_stat_service.domain.Partie;
import com.dauphine.miage.history_stat_service.repository.PartieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valide contre une vraie base (H2) les requêtes JPQL de recherche et d'agrégation :
 * c'est ce test qui garantit que le filtrage/l'agrégation poussés en base sont corrects
 * (les tests du contrôleur, eux, mockent le repository et ne voient pas le SQL).
 */
@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PartieRepositoryTest {

    @Autowired
    private PartieRepository repo;

    private Partie partie(Long joueur, boolean gagne, int tentatives, LocalDateTime dateFin) {
        return partie(joueur, gagne, tentatives, dateFin, "MAISONS");
    }

    private Partie partie(Long joueur, boolean gagne, int tentatives, LocalDateTime dateFin, String motSecret) {
        Partie p = new Partie();
        p.setJoueurId(joueur);
        p.setMotSecret(motSecret);
        p.setNombreTentatives(tentatives);
        p.setGagne(gagne);
        p.setDateFin(dateFin);
        return p;
    }

    @BeforeEach
    void seed() {
        repo.deleteAll();
        repo.save(partie(10L, true,  3, LocalDate.of(2026, 6, 29).atTime(10, 0)));
        repo.save(partie(10L, false, 6, LocalDate.of(2026, 6, 30).atTime(11, 0)));
        repo.save(partie(20L, true,  2, LocalDate.of(2026, 6, 29).atTime(12, 0)));
    }

    @Test
    void search_sansFiltre_retourneToutesLesParties() {
        assertThat(repo.search(null, null, null, null)).hasSize(3);
    }

    @Test
    void search_parJoueur() {
        assertThat(repo.search(10L, null, null, null)).hasSize(2);
    }

    @Test
    void search_parGagne() {
        assertThat(repo.search(null, true,  null, null)).hasSize(2);
        assertThat(repo.search(null, false, null, null)).hasSize(1);
    }

    @Test
    void search_parIntervalleDeJournee() {
        LocalDateTime debut = LocalDate.of(2026, 6, 29).atStartOfDay();
        LocalDateTime fin   = LocalDate.of(2026, 6, 30).atStartOfDay();
        // Les deux parties du 29/06 (celle du 30/06 est exclue par la borne haute stricte)
        assertThat(repo.search(null, null, debut, fin)).hasSize(2);
    }

    @Test
    void search_combineJoueurEtGagne() {
        assertThat(repo.search(10L, true, null, null)).hasSize(1);
    }

    @Test
    void searchByJoueurId_neVoitQueSesPropresParties() {
        assertThat(repo.searchByJoueurId(10L, null, null, null, null)).hasSize(2);
        assertThat(repo.searchByJoueurId(20L, null, null, null, null)).hasSize(1);
    }

    @Test
    void searchByJoueurId_parGagne() {
        assertThat(repo.searchByJoueurId(10L, true,  null, null, null)).hasSize(1);
        assertThat(repo.searchByJoueurId(10L, false, null, null, null)).hasSize(1);
    }

    @Test
    void searchByJoueurId_parMot_sousChaineInsensibleALaCasse() {
        repo.save(partie(10L, true, 4, LocalDate.of(2026, 7, 1).atTime(9, 0), "PARAPLUIE"));

        assertThat(repo.searchByJoueurId(10L, null, null, null, "maison")).hasSize(2);
        assertThat(repo.searchByJoueurId(10L, null, null, null, "para")).hasSize(1);
        assertThat(repo.searchByJoueurId(10L, null, null, null, "inexistant")).isEmpty();
    }

    @Test
    void aggregateClassement_uneLigneParJoueurAvecComptes() {
        List<PartieRepository.ClassementRow> rows = repo.aggregateClassement();

        assertThat(rows).hasSize(2);

        var j10 = rows.stream().filter(r -> r.getJoueurId() == 10L).findFirst().orElseThrow();
        assertThat(j10.getJouees()).isEqualTo(2);
        assertThat(j10.getGagnees()).isEqualTo(1);

        var j20 = rows.stream().filter(r -> r.getJoueurId() == 20L).findFirst().orElseThrow();
        assertThat(j20.getJouees()).isEqualTo(1);
        assertThat(j20.getGagnees()).isEqualTo(1);
    }
}
