package com.dauphine.miage.motus_game_service;

import com.dauphine.miage.motus_game_service.client.DictionaryClient;
import com.dauphine.miage.motus_game_service.client.HistoryClient;
import com.dauphine.miage.motus_game_service.client.PlayerClient;
import com.dauphine.miage.motus_game_service.domain.Jeu;
import com.dauphine.miage.motus_game_service.domain.StatutJeu;
import com.dauphine.miage.motus_game_service.domain.Tentative;
import com.dauphine.miage.motus_game_service.dto.GameStateDto;
import com.dauphine.miage.motus_game_service.repository.JeuRepository;
import com.dauphine.miage.motus_game_service.service.MotusGameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MotusGameServiceTest {

    @Mock JeuRepository    jeuRepository;
    @Mock DictionaryClient dictionaryClient;
    @Mock PlayerClient     playerClient;
    @Mock HistoryClient    historyClient;

    @InjectMocks MotusGameService service;

    // Mot secret de 7 lettres pour tous les tests (règles Motus : 7-10 lettres)
    private static final String MOT_SECRET   = "MAISONS"; // 7 lettres, commence par M
    private static final int    NB_LETTRES   = 7;

    private Jeu jeuEnCours;

    @BeforeEach
    void setUp() {
        jeuEnCours = new Jeu();
        jeuEnCours.setId(1L);
        jeuEnCours.setJoueurId(10L);
        jeuEnCours.setMotSecret(MOT_SECRET);
        jeuEnCours.setStatut(StatutJeu.EN_COURS);
        jeuEnCours.setTentativesMax(6);
        jeuEnCours.setDateDebut(LocalDateTime.now());
        jeuEnCours.setTentatives(new ArrayList<>());
    }

    // ═══════════════════════════════════════════════════════════
    //  startGame()
    // ═══════════════════════════════════════════════════════════

    @Test
    void startGame_joueurInexistant_leveException() {
        when(playerClient.existe(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.startGame(99L, NB_LETTRES))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Joueur")
                .hasMessageContaining("99");
    }

    @Test
    void startGame_nombreLettresInvalide_leveException() {
        assertThatThrownBy(() -> service.startGame(10L, 5))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("5");
    }

    @Test
    void startGame_succes_retourneDtoCorrect() {
        when(playerClient.existe(10L)).thenReturn(true);
        when(dictionaryClient.getMotAleatoireParLongueur(NB_LETTRES)).thenReturn(MOT_SECRET);
        when(jeuRepository.save(any(Jeu.class))).thenAnswer(inv -> {
            Jeu j = inv.getArgument(0);
            j.setId(42L);
            return j;
        });

        GameStateDto dto = service.startGame(10L, NB_LETTRES);

        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getJoueurId()).isEqualTo(10L);
        assertThat(dto.getStatut()).isEqualTo("EN_COURS");
        assertThat(dto.getMotSecret()).isNull();
        assertThat(dto.getPremiereLettre()).isEqualTo("M");
        assertThat(dto.getLongueurMot()).isEqualTo(NB_LETTRES);
        assertThat(dto.getTentativesMax()).isEqualTo(6);
        assertThat(dto.getTentativesRestantes()).isEqualTo(6);
        assertThat(dto.getTentatives()).isEmpty();
    }

    @Test
    void startGame_dictionnaireRetourneNull_leveException() {
        when(playerClient.existe(10L)).thenReturn(true);
        when(dictionaryClient.getMotAleatoireParLongueur(NB_LETTRES)).thenReturn(null);

        assertThatThrownBy(() -> service.startGame(10L, NB_LETTRES))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("mot");
    }

    // ═══════════════════════════════════════════════════════════
    //  guess()
    // ═══════════════════════════════════════════════════════════

    @Test
    void guess_partieInexistante_leveException() {
        when(jeuRepository.findByIdWithTentatives(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.guess(99L, MOT_SECRET))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    @Test
    void guess_partieDejaTerminee_leveException() {
        jeuEnCours.setStatut(StatutJeu.GAGNE);
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        assertThatThrownBy(() -> service.guess(1L, MOT_SECRET))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("terminée");
    }

    @Test
    void guess_longueurIncorrecte_leveException() {
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        assertThatThrownBy(() -> service.guess(1L, "MOT"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(String.valueOf(NB_LETTRES));
    }

    @Test
    void guess_premiereLettreMauvaise_leveException() {
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        // "GARAGES" commence par G, pas par M comme MAISONS
        assertThatThrownBy(() -> service.guess(1L, "GARAGES"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("M");
    }

    @Test
    void guess_motDejaPropose_leveException() {
        Tentative precedente = new Tentative();
        precedente.setNumero(1);
        precedente.setMotPropose("MERCURE");
        precedente.setFeedback("ABSENT,ABSENT,ABSENT,ABSENT,ABSENT,ABSENT,ABSENT");
        precedente.setJeu(jeuEnCours);
        jeuEnCours.getTentatives().add(precedente);

        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        assertThatThrownBy(() -> service.guess(1L, "MERCURE"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("déjà été proposé");
    }

    @Test
    void guess_motInconnu_leveException() {
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));
        when(dictionaryClient.motExiste("MXXXXXX")).thenReturn(false);

        assertThatThrownBy(() -> service.guess(1L, "MXXXXXX"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MXXXXXX");
    }

    @Test
    void guess_motCorrect_retourneStatutGagne() {
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));
        when(dictionaryClient.motExiste(MOT_SECRET)).thenReturn(true);
        when(jeuRepository.save(any(Jeu.class))).thenAnswer(inv -> inv.getArgument(0));

        GameStateDto dto = service.guess(1L, MOT_SECRET);

        assertThat(dto.getStatut()).isEqualTo("GAGNE");
        assertThat(dto.getMotSecret()).isEqualTo(MOT_SECRET);
        assertThat(dto.getTentativesEffectuees()).isEqualTo(1);
        assertThat(dto.getMessage()).contains("1");
        var lettres = dto.getTentatives().get(0).getLettres();
        assertThat(lettres).allMatch(l -> "BIEN_PLACE".equals(l.getResultat()));
    }

    @Test
    void guess_sixiemeTentativeRatee_retourneStatutPerdu() {
        // 5 tentatives précédentes avec des mots différents (règle Motus : pas de répétition)
        String[] motsPrecedents = { "MERCURE", "MONTAGE", "MIROIRS", "MUSIQUE", "MARRONS" };
        for (int i = 0; i < 5; i++) {
            Tentative t = new Tentative();
            t.setNumero(i + 1);
            t.setMotPropose(motsPrecedents[i]);
            t.setFeedback("ABSENT,ABSENT,ABSENT,ABSENT,ABSENT,ABSENT,ABSENT");
            t.setJeu(jeuEnCours);
            jeuEnCours.getTentatives().add(t);
        }

        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));
        when(dictionaryClient.motExiste("MINUTES")).thenReturn(true);
        when(jeuRepository.save(any(Jeu.class))).thenAnswer(inv -> inv.getArgument(0));

        GameStateDto dto = service.guess(1L, "MINUTES"); // 6e tentative, différente des précédentes

        assertThat(dto.getStatut()).isEqualTo("PERDU");
        assertThat(dto.getMotSecret()).isEqualTo(MOT_SECRET);
        assertThat(dto.getTentativesEffectuees()).isEqualTo(6);
        assertThat(dto.getTentativesRestantes()).isEqualTo(0);
        assertThat(dto.getMessage()).contains(MOT_SECRET);
    }

    @Test
    void guess_tentativeIntermediaire_statutEnCours() {
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));
        when(dictionaryClient.motExiste("MERCURE")).thenReturn(true);
        when(jeuRepository.save(any(Jeu.class))).thenAnswer(inv -> inv.getArgument(0));

        GameStateDto dto = service.guess(1L, "MERCURE");

        assertThat(dto.getStatut()).isEqualTo("EN_COURS");
        assertThat(dto.getMotSecret()).isNull();
        assertThat(dto.getTentativesEffectuees()).isEqualTo(1);
        assertThat(dto.getTentativesRestantes()).isEqualTo(5);
        assertThat(dto.getMessage()).contains("5").contains("restant");
    }

    // ═══════════════════════════════════════════════════════════
    //  getGame() — mapping DTO
    // ═══════════════════════════════════════════════════════════

    @Test
    void getGame_partieEnCours_motSecretMasque() {
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        GameStateDto dto = service.getGame(1L);

        assertThat(dto.getMotSecret()).isNull();
        assertThat(dto.getPremiereLettre()).isEqualTo("M");
        assertThat(dto.getLongueurMot()).isEqualTo(NB_LETTRES);
        assertThat(dto.getStatut()).isEqualTo("EN_COURS");
    }

    @Test
    void getGame_partieGagnee_motSecretRevele() {
        jeuEnCours.setStatut(StatutJeu.GAGNE);
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        assertThat(service.getGame(1L).getMotSecret()).isEqualTo(MOT_SECRET);
    }

    @Test
    void getGame_partiePerdee_motSecretRevele() {
        jeuEnCours.setStatut(StatutJeu.PERDU);
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        assertThat(service.getGame(1L).getMotSecret()).isEqualTo(MOT_SECRET);
    }

    @Test
    void getGame_compteursCorrects() {
        Tentative t = new Tentative();
        t.setNumero(1); t.setMotPropose("MERCURE");
        t.setFeedback("ABSENT,BIEN_PLACE,ABSENT,ABSENT,ABSENT,ABSENT,ABSENT");
        t.setJeu(jeuEnCours);
        jeuEnCours.getTentatives().add(t);

        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        GameStateDto dto = service.getGame(1L);
        assertThat(dto.getTentativesEffectuees()).isEqualTo(1);
        assertThat(dto.getTentativesRestantes()).isEqualTo(5);
        assertThat(dto.getTentativesMax()).isEqualTo(6);
    }

    @Test
    void getGame_feedbackStructureLettresCorrectes() {
        Tentative t = new Tentative();
        t.setNumero(1); t.setMotPropose("MERCURE");
        // M E R C U R E — feedback sur 7 lettres
        t.setFeedback("ABSENT,BIEN_PLACE,ABSENT,MAL_PLACE,ABSENT,ABSENT,ABSENT");
        t.setJeu(jeuEnCours);
        jeuEnCours.getTentatives().add(t);

        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        var lettres = service.getGame(1L).getTentatives().get(0).getLettres();
        assertThat(lettres).hasSize(NB_LETTRES);
        assertThat(lettres.get(0).getLettre()).isEqualTo("M");
        assertThat(lettres.get(0).getResultat()).isEqualTo("ABSENT");
        assertThat(lettres.get(1).getResultat()).isEqualTo("BIEN_PLACE");
        assertThat(lettres.get(3).getResultat()).isEqualTo("MAL_PLACE");
    }

    @Test
    void getGame_messageGagne_contientTrouve() {
        jeuEnCours.setStatut(StatutJeu.GAGNE);
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        assertThat(service.getGame(1L).getMessage()).containsIgnoringCase("trouvé");
    }

    @Test
    void getGame_messagePerdu_contientMotSecret() {
        jeuEnCours.setStatut(StatutJeu.PERDU);
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        assertThat(service.getGame(1L).getMessage()).contains(MOT_SECRET);
    }

    @Test
    void getGame_messageEnCours_contientRestant() {
        when(jeuRepository.findByIdWithTentatives(1L)).thenReturn(Optional.of(jeuEnCours));

        assertThat(service.getGame(1L).getMessage()).contains("6").contains("restant");
    }

    @Test
    void getGame_partieInexistante_leveException() {
        when(jeuRepository.findByIdWithTentatives(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGame(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }
}
