package com.dauphine.miage.history_stat_service;

import com.dauphine.miage.history_stat_service.controller.HistoryController;
import com.dauphine.miage.history_stat_service.domain.Partie;
import com.dauphine.miage.history_stat_service.dto.ClassementDto;
import com.dauphine.miage.history_stat_service.dto.StatDto;
import com.dauphine.miage.history_stat_service.repository.PartieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryControllerTest {

    @Mock
    PartieRepository partieRepository;

    @InjectMocks
    HistoryController historyController;

    private Partie partieGagnee;
    private Partie partiePerdue;

    @BeforeEach
    void setUp() {
        partieGagnee = new Partie();
        partieGagnee.setId(1L);
        partieGagnee.setJoueurId(10L);
        partieGagnee.setMotSecret("MAISON");
        partieGagnee.setNombreTentatives(3);
        partieGagnee.setGagne(true);
        partieGagnee.setDateFin(LocalDateTime.now());

        partiePerdue = new Partie();
        partiePerdue.setId(2L);
        partiePerdue.setJoueurId(10L);
        partiePerdue.setMotSecret("JARDIN");
        partiePerdue.setNombreTentatives(6);
        partiePerdue.setGagne(false);
        partiePerdue.setDateFin(LocalDateTime.now());
    }

    // ── Enregistrement ────────────────────────────────────────────────────────

    @Test
    void savePartie_retourne201AvecPartie() {
        when(partieRepository.save(any())).thenReturn(partieGagnee);

        ResponseEntity<Partie> response = historyController.savePartie(partieGagnee);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMotSecret()).isEqualTo("MAISON");
    }

    // ── Historique ────────────────────────────────────────────────────────────

    @Test
    void getHistoryByPlayer_retourneListeParties() {
        when(partieRepository.findByJoueurId(10L)).thenReturn(List.of(partieGagnee, partiePerdue));

        List<Partie> result = historyController.getHistoryByPlayer(10L);

        assertThat(result).hasSize(2);
    }

    @Test
    void getHistoryByPlayer_aucunePartie_retourneListeVide() {
        when(partieRepository.findByJoueurId(99L)).thenReturn(List.of());

        List<Partie> result = historyController.getHistoryByPlayer(99L);

        assertThat(result).isEmpty();
    }

    // ── Statistiques ──────────────────────────────────────────────────────────

    @Test
    void getStatsByPlayer_retourneStatsCorrectes() {
        when(partieRepository.findByJoueurId(10L)).thenReturn(List.of(partieGagnee, partiePerdue));

        ResponseEntity<StatDto> response = historyController.getStatsByPlayer(10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StatDto stats = response.getBody();
        assertThat(stats).isNotNull();
        assertThat(stats.getPartiesJouees()).isEqualTo(2);
        assertThat(stats.getPartiesGagnees()).isEqualTo(1);
        assertThat(stats.getTauxVictoire()).isEqualTo(50.0);
        assertThat(stats.getMoyenneTentatives()).isEqualTo(4.5);
    }

    @Test
    void getStatsByPlayer_aucunePartie_retourne404() {
        when(partieRepository.findByJoueurId(99L)).thenReturn(List.of());

        ResponseEntity<StatDto> response = historyController.getStatsByPlayer(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Classement ────────────────────────────────────────────────────────────

    @Test
    void getClassement_ordonneParVictoiresDecroissantes() {
        Partie partieJ2 = new Partie();
        partieJ2.setJoueurId(20L);
        partieJ2.setNombreTentatives(2);
        partieJ2.setGagne(true);

        when(partieRepository.findAll()).thenReturn(
                List.of(partieGagnee, partiePerdue, partieJ2));

        List<ClassementDto> classement = historyController.getClassement();

        // joueur 10 : 1 victoire, joueur 20 : 1 victoire → rang basé sur taux
        assertThat(classement).hasSize(2);
        // Le premier rang doit avoir le rang = 1
        assertThat(classement.get(0).getRang()).isEqualTo(1);
        assertThat(classement.get(1).getRang()).isEqualTo(2);
    }

    @Test
    void getClassement_tableauVide_retourneListeVide() {
        when(partieRepository.findAll()).thenReturn(List.of());

        List<ClassementDto> classement = historyController.getClassement();

        assertThat(classement).isEmpty();
    }

    // ── Recherche admin ───────────────────────────────────────────────────────

    @Test
    void searchParties_filtreParJoueur() {
        when(partieRepository.findAll()).thenReturn(List.of(partieGagnee, partiePerdue));

        List<Partie> result = historyController.searchParties(10L, null, null);

        assertThat(result).hasSize(2); // les deux appartiennent au joueur 10
    }

    @Test
    void searchParties_filtreParGagne() {
        when(partieRepository.findAll()).thenReturn(List.of(partieGagnee, partiePerdue));

        List<Partie> gagnees = historyController.searchParties(null, null, true);
        List<Partie> perdues = historyController.searchParties(null, null, false);

        assertThat(gagnees).hasSize(1);
        assertThat(perdues).hasSize(1);
    }

    @Test
    void searchParties_filtreParJoueurEtGagne() {
        Partie autreJoueur = new Partie();
        autreJoueur.setJoueurId(20L);
        autreJoueur.setGagne(true);
        autreJoueur.setNombreTentatives(1);

        when(partieRepository.findAll()).thenReturn(
                List.of(partieGagnee, partiePerdue, autreJoueur));

        List<Partie> result = historyController.searchParties(10L, null, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getJoueurId()).isEqualTo(10L);
        assertThat(result.get(0).isGagne()).isTrue();
    }
}
