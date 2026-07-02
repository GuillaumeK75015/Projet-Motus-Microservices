package com.dauphine.miage.motus_game_service;

import com.dauphine.miage.motus_game_service.util.MotusFeedbackUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de l'algorithme de feedback Motus.
 * Aucune dépendance Spring ou base de données.
 */
class MotusFeedbackUtilTest {

    // ── Cas simples ───────────────────────────────────────────────────────────

    @Test
    void toutesLesTresLettresBienPlacees_retourneToutBienPlace() {
        String[] fb = MotusFeedbackUtil.compute("MAISON", "MAISON");
        assertThat(fb).containsOnly("BIEN_PLACE");
    }

    @Test
    void aucuneLettreCommuneAuMotSecret_retourneToutAbsent() {
        String[] fb = MotusFeedbackUtil.compute("ABCDEF", "GHIJKL");
        assertThat(fb).containsOnly("ABSENT");
    }

    @Test
    void anagrammeParfait_retourneToutMalPlace() {
        // ABCD vs BCDA : toutes les lettres présentes mais décalées
        String[] fb = MotusFeedbackUtil.compute("ABCD", "BCDA");
        assertThat(fb).containsOnly("MAL_PLACE");
    }

    // ── Positions mixtes ──────────────────────────────────────────────────────

    @Test
    void mixte_bienPlaceEtAbsent() {
        // Secret : PORTE
        // Guess  : PONTE → P=BIEN, O=BIEN, N=ABSENT, T=BIEN, E=BIEN
        String[] fb = MotusFeedbackUtil.compute("PONTE", "PORTE");
        assertThat(fb[0]).isEqualTo("BIEN_PLACE"); // P
        assertThat(fb[1]).isEqualTo("BIEN_PLACE"); // O
        assertThat(fb[2]).isEqualTo("ABSENT");     // N
        assertThat(fb[3]).isEqualTo("BIEN_PLACE"); // T
        assertThat(fb[4]).isEqualTo("BIEN_PLACE"); // E
    }

    @Test
    void mixte_bienPlaceEtMalPlace() {
        // Secret : ARBRE
        // Guess  : RBARE → R=MAL_PLACE, B=MAL_PLACE, A=MAL_PLACE, R=BIEN_PLACE, E=BIEN_PLACE
        String[] fb = MotusFeedbackUtil.compute("RBARE", "ARBRE");
        assertThat(fb[3]).isEqualTo("BIEN_PLACE"); // R en position 3
        assertThat(fb[4]).isEqualTo("BIEN_PLACE"); // E en position 4
    }

    // ── Gestion des lettres en double ─────────────────────────────────────────

    @Test
    void lettreEnDoubleGuess_uneSeuleOccurrenceComptee() {
        // Secret : MAISON (M une fois)
        // Guess  : MMISON → M[0]=BIEN_PLACE, M[1]=ABSENT (M déjà consommé)
        String[] fb = MotusFeedbackUtil.compute("MMISON", "MAISON");
        assertThat(fb[0]).isEqualTo("BIEN_PLACE"); // premier M bien placé
        assertThat(fb[1]).isEqualTo("ABSENT");     // deuxième M : M déjà consommé
    }

    @Test
    void lettreEnDoubleSecret_deuxOccurrencesGerees() {
        // Secret : LEVER (E en pos 1 et 3)
        // Guess  : EELER → E[0]=MAL_PLACE (E existe), E[1]=BIEN_PLACE (pos 1=E), ...
        String[] fb = MotusFeedbackUtil.compute("EELER", "LEVER");
        // Passe 1 : pos1=E==E → BIEN_PLACE ; pos3=L vs E → non; pos4=R==R → BIEN_PLACE
        assertThat(fb[1]).isEqualTo("BIEN_PLACE"); // E à la bonne position
        assertThat(fb[4]).isEqualTo("BIEN_PLACE"); // R bien placé
    }

    @Test
    void lettreBienPlaceeNePasReutiliserPourMalPlace() {
        // Secret : RACES  (R[0]-A[1]-C[2]-E[3]-S[4])
        // Guess  : ARCER  (A[0]-R[1]-C[2]-E[3]-R[4])
        // Passe 1 : C[2]=C → BIEN_PLACE ; E[3]=E → BIEN_PLACE
        // Passe 2 : A[0] → MAL_PLACE (A en pos 1 du secret) ;
        //           R[1] → MAL_PLACE (R en pos 0 du secret) ;
        //           R[4] → ABSENT (le seul R du secret est déjà consommé)
        String[] fb = MotusFeedbackUtil.compute("ARCER", "RACES");
        assertThat(fb[0]).isEqualTo("MAL_PLACE");  // A présent dans RACES (pos 1)
        assertThat(fb[1]).isEqualTo("MAL_PLACE");  // R présent dans RACES (pos 0), mais pas en pos 1
        assertThat(fb[2]).isEqualTo("BIEN_PLACE"); // C bien placé
        assertThat(fb[3]).isEqualTo("BIEN_PLACE"); // E bien placé
        assertThat(fb[4]).isEqualTo("ABSENT");     // R déjà consommé — ne compte pas deux fois
    }

    // ── Cas limites ───────────────────────────────────────────────────────────

    @Test
    void motDUneLettreCorrect() {
        String[] fb = MotusFeedbackUtil.compute("A", "A");
        assertThat(fb).containsExactly("BIEN_PLACE");
    }

    @Test
    void motDUneLettreIncorrect() {
        String[] fb = MotusFeedbackUtil.compute("A", "B");
        assertThat(fb).containsExactly("ABSENT");
    }

    @Test
    void tailleFeedbackEgaleTailleSecret() {
        String[] fb = MotusFeedbackUtil.compute("ABCDEF", "UVWXYZ");
        assertThat(fb).hasSize(6);
    }
}
