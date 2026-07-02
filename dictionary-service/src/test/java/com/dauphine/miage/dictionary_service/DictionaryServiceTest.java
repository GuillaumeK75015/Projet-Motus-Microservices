package com.dauphine.miage.dictionary_service;

import com.dauphine.miage.dictionary_service.service.DictionaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du DictionaryService.
 * On instancie le service sans Spring et on ajoute les mots manuellement
 * (le @PostConstruct qui charge mots.txt n'est pas invoqué hors contexte Spring).
 *
 * Tous les mots de test respectent les règles Motus : 7-10 lettres, uniquement A-Z,
 * pas de tiret ni d'apostrophe, pas de nom propre.
 */
class DictionaryServiceTest {

    private DictionaryService service;

    @BeforeEach
    void setUp() {
        service = new DictionaryService();
        // Mots de 7 lettres — format valide pour Motus (règles en vigueur depuis 2010)
        service.addWord("MAISONS");
        service.addWord("JARDINS");
        service.addWord("SOLEILS");
        service.addWord("CHEMINS");
        service.addWord("VOYAGES");
    }

    // ── Mot aléatoire ─────────────────────────────────────────────────────────

    @Test
    void getRandomWord_retourneUnMot() {
        Optional<String> mot = service.getRandomWord();
        assertThat(mot).isPresent();
        assertThat(mot.get()).isNotBlank();
    }

    @Test
    void getRandomWord_motAppartientAuDictionnaire() {
        Optional<String> mot = service.getRandomWord();
        assertThat(mot).isPresent();
        assertThat(service.wordExists(mot.get())).isTrue();
    }

    @Test
    void getRandomWord_pasDuplicationAvantEpuisement() {
        // 5 mots dans le dico → les 5 premiers appels doivent être tous différents
        Set<String> tirages = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            String mot = service.getRandomWord().orElseThrow();
            assertThat(tirages).doesNotContain(mot);
            tirages.add(mot);
        }
        assertThat(tirages).hasSize(5);
    }

    @Test
    void getRandomWord_apresEpuisementRecommenceCycle() {
        // Épuiser les 5 mots de 7 lettres
        for (int i = 0; i < 5; i++) service.getRandomWord();

        // Le 6e appel doit quand même retourner quelque chose (cycle recommence)
        Optional<String> mot = service.getRandomWord();
        assertThat(mot).isPresent();
        assertThat(service.wordExists(mot.get())).isTrue();
    }

    @Test
    void getRandomWord_dictionnaireVide_retourneEmpty() {
        DictionaryService vide = new DictionaryService();
        assertThat(vide.getRandomWord()).isEmpty();
    }

    // ── Mot aléatoire par longueur ─────────────────────────────────────────────

    @Test
    void getRandomWordByLength_longueurPresente_retourneUnMot() {
        Optional<String> mot = service.getRandomWordByLength(7);
        assertThat(mot).isPresent();
        assertThat(mot.get()).hasSize(7);
    }

    @Test
    void getRandomWordByLength_longueurAbsente_retourneEmpty() {
        // Aucun mot de 10 lettres n'a été ajouté
        assertThat(service.getRandomWordByLength(10)).isEmpty();
    }

    // ── Existence ─────────────────────────────────────────────────────────────

    @Test
    void wordExists_motPresent_retourneTrue() {
        assertThat(service.wordExists("MAISONS")).isTrue();
    }

    @Test
    void wordExists_motAbsent_retourneFalse() {
        assertThat(service.wordExists("CHATEAUX")).isFalse();
    }

    @Test
    void wordExists_caseInsensitive() {
        assertThat(service.wordExists("maisons")).isTrue();
        assertThat(service.wordExists("Maisons")).isTrue();
    }

    // ── Ajout / suppression ───────────────────────────────────────────────────

    @Test
    void addWord_ajouteMot() {
        service.addWord("OCEANIE"); // 7 lettres
        assertThat(service.wordExists("OCEANIE")).isTrue();
        assertThat(service.getAllWords()).contains("OCEANIE");
    }

    @Test
    void addWord_motTropCourt_refuseEtAbsent() {
        // Mots de 6 lettres refusés (règle Motus : 7 lettres minimum)
        service.addWord("MAISON");
        assertThat(service.wordExists("MAISON")).isFalse();
    }

    @Test
    void addWord_nePasDupliquer() {
        int avant = service.getAllWords().size();
        service.addWord("MAISONS"); // déjà présent
        assertThat(service.getAllWords()).hasSize(avant);
    }

    @Test
    void removeWord_supprimeMotExistant() {
        boolean resultat = service.removeWord("MAISONS");
        assertThat(resultat).isTrue();
        assertThat(service.wordExists("MAISONS")).isFalse();
    }

    @Test
    void removeWord_motAbsent_retourneFalse() {
        boolean resultat = service.removeWord("INCONNU");
        assertThat(resultat).isFalse();
    }

    // ── Liste ─────────────────────────────────────────────────────────────────

    @Test
    void getAllWords_retourneTousLesMots() {
        assertThat(service.getAllWords()).containsExactlyInAnyOrder(
                "MAISONS", "JARDINS", "SOLEILS", "CHEMINS", "VOYAGES");
    }

    // ── Stats admin ───────────────────────────────────────────────────────────

    @Test
    void getStatsByLength_contientLongueur7() {
        var stats = service.getStatsByLength();
        assertThat(stats).containsKey(7);
        assertThat(stats.get(7)).isEqualTo(5);
    }

    @Test
    void journalAdmin_contientEntreesApresActions() {
        service.addWord("FENETRE"); // 7 lettres — "FENETRE" normalise en "FENETRE"
        service.removeWord("JARDINS");

        assertThat(service.getJournalAdmin()).isNotEmpty();
    }
}
