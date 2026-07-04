package com.dauphine.miage.history_stat_service;

import com.dauphine.miage.history_stat_service.controller.HistoryController;
import com.dauphine.miage.history_stat_service.domain.Partie;
import com.dauphine.miage.history_stat_service.dto.ClassementDto;
import com.dauphine.miage.history_stat_service.dto.StatDto;
import com.dauphine.miage.history_stat_service.repository.PartieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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

    // L'agrégation étant faite en base, on simule les lignes agrégées (une par joueur).
    private PartieRepository.ClassementRow row(long joueurId, long jouees, long gagnees) {
        return new PartieRepository.ClassementRow() {
            public Long getJoueurId() { return joueurId; }
            public long getJouees()   { return jouees; }
            public long getGagnees()  { return gagnees; }
        };
    }

    @Test
    void getClassement_calculeTauxEtRang() {
        // joueur 10 : 2 jouées / 1 gagnée (50 %) ; joueur 20 : 1 jouée / 1 gagnée (100 %)
        when(partieRepository.aggregateClassement())
                .thenReturn(List.of(row(10L, 2, 1), row(20L, 1, 1)));

        List<ClassementDto> classement = historyController.getClassement();

        assertThat(classement).hasSize(2);
        assertThat(classement.get(0).getRang()).isEqualTo(1);
        assertThat(classement.get(1).getRang()).isEqualTo(2);

        ClassementDto j10 = classement.stream().filter(c -> c.getJoueurId() == 10L).findFirst().orElseThrow();
        assertThat(j10.getPartiesJouees()).isEqualTo(2);
        assertThat(j10.getPartiesGagnees()).isEqualTo(1);
        assertThat(j10.getTauxVictoire()).isEqualTo(50.0);
    }

    // Les deux joueurs de getClassement_calculeTauxEtRang ont le même nombre de victoires (1) :
    // un tri qui inverserait par erreur le nombre de victoires (au lieu du seul taux en cas
    // d'égalité) passerait quand même ce test. Ce cas couvre explicitement des victoires
    // différentes, pour garantir que le joueur le plus gagnant est bien classé premier.
    @Test
    void getClassement_trieParVictoiresDecroissantes() {
        // joueur 1 : 1 jouée / 0 gagnée ; joueur 2 : 6 jouées / 5 gagnées
        when(partieRepository.aggregateClassement())
                .thenReturn(List.of(row(1L, 1, 0), row(2L, 6, 5)));

        List<ClassementDto> classement = historyController.getClassement();

        assertThat(classement.get(0).getJoueurId()).isEqualTo(2L);
        assertThat(classement.get(0).getRang()).isEqualTo(1);
        assertThat(classement.get(1).getJoueurId()).isEqualTo(1L);
        assertThat(classement.get(1).getRang()).isEqualTo(2);
    }

    @Test
    void getClassement_tableauVide_retourneListeVide() {
        when(partieRepository.aggregateClassement()).thenReturn(List.of());

        List<ClassementDto> classement = historyController.getClassement();

        assertThat(classement).isEmpty();
    }

    // ── Recherche admin (filtrage délégué à la base) ──────────────────────────

    @Test
    void searchParties_delegueAuRepository() {
        when(partieRepository.search(eq(10L), isNull(), isNull(), isNull()))
                .thenReturn(List.of(partieGagnee, partiePerdue));

        List<Partie> result = historyController.searchParties(10L, null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void searchParties_filtreGagne_passeLeParametre() {
        when(partieRepository.search(isNull(), eq(true), isNull(), isNull()))
                .thenReturn(List.of(partieGagnee));
        when(partieRepository.search(isNull(), eq(false), isNull(), isNull()))
                .thenReturn(List.of(partiePerdue));

        assertThat(historyController.searchParties(null, null, true)).hasSize(1);
        assertThat(historyController.searchParties(null, null, false)).hasSize(1);
    }

    @Test
    void searchParties_dateTraduiteEnIntervalleDeJournee() {
        LocalDate date = LocalDate.of(2026, 6, 29);
        when(partieRepository.search(isNull(), isNull(), any(), any())).thenReturn(List.of());

        historyController.searchParties(null, date, null);

        ArgumentCaptor<LocalDateTime> debut = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fin   = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(partieRepository).search(isNull(), isNull(), debut.capture(), fin.capture());

        assertThat(debut.getValue()).isEqualTo(date.atStartOfDay());
        assertThat(fin.getValue()).isEqualTo(date.plusDays(1).atStartOfDay());
    }
}
