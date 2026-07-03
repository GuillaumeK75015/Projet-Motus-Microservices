package com.dauphine.miage.motus_game_service.service;

import com.dauphine.miage.motus_game_service.client.DictionaryClient;
import com.dauphine.miage.motus_game_service.client.HistoryClient;
import com.dauphine.miage.motus_game_service.client.PlayerClient;
import com.dauphine.miage.motus_game_service.domain.Jeu;
import com.dauphine.miage.motus_game_service.domain.StatutJeu;
import com.dauphine.miage.motus_game_service.domain.Tentative;
import com.dauphine.miage.motus_game_service.dto.GameStateDto;
import com.dauphine.miage.motus_game_service.dto.LettreResultat;
import com.dauphine.miage.motus_game_service.dto.TentativeDto;
import com.dauphine.miage.motus_game_service.repository.JeuRepository;
import com.dauphine.miage.motus_game_service.util.MotusFeedbackUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class MotusGameService {

    private static final Logger log      = LoggerFactory.getLogger(MotusGameService.class);
    private static final Logger adminLog = LoggerFactory.getLogger("ADMIN.game");

    private static final int LONGUEUR_MIN = 7;
    private static final int LONGUEUR_MAX = 10;

    private static final Random random = new Random();

    @Autowired private JeuRepository    jeuRepository;
    @Autowired private DictionaryClient dictionaryClient;
    @Autowired private PlayerClient     playerClient;
    @Autowired private HistoryClient    historyClient;

    // ── Démarrage ─────────────────────────────────────────────────────────────

    /**
     * Démarre une nouvelle partie.
     *
     * @param joueurId     identifiant du joueur
     * @param nombreLettres longueur du mot à trouver (7-10) ; tirée au hasard entre 7 et 10 si null
     */
    @Transactional
    public GameStateDto startGame(Long joueurId, Integer nombreLettres) {
        // Validation de la longueur demandée (tirage aléatoire si non précisée)
        int longueur = (nombreLettres != null) ? nombreLettres : tirerLongueurAleatoire();
        if (longueur < LONGUEUR_MIN || longueur > LONGUEUR_MAX) {
            String msg = "Nombre de lettres invalide : " + longueur + ". Doit être entre " + LONGUEUR_MIN + " et " + LONGUEUR_MAX;
            adminLog.warn("[ADMIN] Partie refusée — joueur {} : {}", joueurId, msg);
            throw new RuntimeException(msg);
        }

        // Vérification que le joueur existe
        if (!playerClient.existe(joueurId)) {
            throw new RuntimeException("Joueur introuvable avec l'id : " + joueurId);
        }

        // Tirage du mot secret (longueur précise)
        String mot;
        try {
            mot = dictionaryClient.getMotAleatoireParLongueur(longueur);
        } catch (Exception e) {
            throw new RuntimeException("Impossible de contacter le dictionnaire pour " + longueur + " lettres");
        }
        if (mot == null || mot.isBlank()) {
            throw new RuntimeException("Aucun mot disponible de " + longueur + " lettres dans le dictionnaire");
        }

        String motSecret = mot.toUpperCase().trim().replace("\"", "");
        dictionaryClient.ajouterAuCache(motSecret);

        Jeu jeu = new Jeu();
        jeu.setJoueurId(joueurId);
        jeu.setMotSecret(motSecret);
        jeu.setStatut(StatutJeu.EN_COURS);
        jeu.setDateDebut(LocalDateTime.now());
        jeu.setTentativesMax(6);

        GameStateDto dto = toDto(jeuRepository.save(jeu));
        adminLog.info("[ADMIN] Partie {} démarrée — joueur={}, longueur={}, premièreLettre={}",
                dto.getId(), joueurId, longueur, dto.getPremiereLettre());
        log.info("Partie {} démarrée pour le joueur {} ({} lettres)", dto.getId(), joueurId, longueur);
        return dto;
    }

    // ── Tentative ─────────────────────────────────────────────────────────────

    @Transactional
    public GameStateDto guess(Long jeuId, String motPropose) {
        Jeu jeu = jeuRepository.findByIdWithTentatives(jeuId)
                .orElseThrow(() -> new RuntimeException("Partie introuvable avec l'id : " + jeuId));

        if (jeu.getStatut() != StatutJeu.EN_COURS) {
            throw new RuntimeException("Cette partie est terminée (statut : " + jeu.getStatut() + ")");
        }

        String mot = motPropose.toUpperCase().trim();
        String motSecret = jeu.getMotSecret();
        char premiereLettre = motSecret.charAt(0);

        // Règle 1 : longueur exacte
        if (mot.length() != motSecret.length()) {
            String raison = "Le mot doit contenir " + motSecret.length() + " lettres, vous en avez saisi " + mot.length();
            adminLog.info("[ADMIN] Partie {} — mot refusé '{}' : {}", jeuId, mot, raison);
            throw new RuntimeException(raison);
        }

        // Règle 2 : première lettre correcte
        if (mot.charAt(0) != premiereLettre) {
            String raison = "Le mot doit commencer par la lettre '" + premiereLettre + "' (proposé : '" + mot.charAt(0) + "')";
            adminLog.info("[ADMIN] Partie {} — mot refusé '{}' : {}", jeuId, mot, raison);
            throw new RuntimeException(raison);
        }

        // Règle 3 : mot non déjà proposé dans cette partie
        boolean dejaPropose = jeu.getTentatives().stream()
                .anyMatch(t -> t.getMotPropose().equalsIgnoreCase(mot));
        if (dejaPropose) {
            String raison = "Le mot '" + mot + "' a déjà été proposé dans cette partie";
            adminLog.info("[ADMIN] Partie {} — mot refusé '{}' : {}", jeuId, mot, raison);
            throw new RuntimeException(raison);
        }

        // Règle 4 : mot présent dans le dictionnaire
        if (!dictionaryClient.motExiste(mot)) {
            String raison = "Mot non reconnu dans le dictionnaire : " + mot;
            adminLog.info("[ADMIN] Partie {} — mot refusé '{}' : {}", jeuId, mot, raison);
            throw new RuntimeException(raison);
        }

        // Calcul du feedback
        String[] feedbackArray = MotusFeedbackUtil.compute(mot, motSecret);

        Tentative tentative = new Tentative();
        tentative.setJeu(jeu);
        tentative.setNumero(jeu.getTentatives().size() + 1);
        tentative.setMotPropose(mot);
        tentative.setFeedback(String.join(",", feedbackArray));
        jeu.getTentatives().add(tentative);

        boolean allCorrect = Arrays.stream(feedbackArray).allMatch("BIEN_PLACE"::equals);

        if (allCorrect) {
            jeu.setStatut(StatutJeu.GAGNE);
            adminLog.info("[ADMIN] Partie {} — GAGNÉE par joueur {} en {} essai(s) (mot : {})",
                    jeuId, jeu.getJoueurId(), jeu.getTentatives().size(), motSecret);
            enregistrerHistorique(jeu, true);
        } else if (jeu.getTentatives().size() >= jeu.getTentativesMax()) {
            jeu.setStatut(StatutJeu.PERDU);
            adminLog.info("[ADMIN] Partie {} — PERDUE par joueur {} après {} essais (mot secret : {})",
                    jeuId, jeu.getJoueurId(), jeu.getTentatives().size(), motSecret);
            enregistrerHistorique(jeu, false);
        }

        return toDto(jeuRepository.save(jeu));
    }

    // ── Lecture (readOnly = true → snapshot isolation, pas de dirty check) ────

    @Transactional(readOnly = true)
    public GameStateDto getGame(Long jeuId) {
        return jeuRepository.findByIdWithTentatives(jeuId)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("Partie introuvable avec l'id : " + jeuId));
    }

    @Transactional(readOnly = true)
    public List<GameStateDto> getGamesByPlayer(Long joueurId) {
        return jeuRepository.findByJoueurIdWithTentatives(joueurId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GameStateDto> getAllGames() {
        return jeuRepository.findAllWithTentatives()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    private static int tirerLongueurAleatoire() {
        return LONGUEUR_MIN + random.nextInt(LONGUEUR_MAX - LONGUEUR_MIN + 1);
    }

    // ── Conversion entité → DTO ───────────────────────────────────────────────

    private GameStateDto toDto(Jeu jeu) {
        GameStateDto dto = new GameStateDto();
        dto.setId(jeu.getId());
        dto.setJoueurId(jeu.getJoueurId());
        dto.setStatut(jeu.getStatut().name());
        dto.setPremiereLettre(String.valueOf(jeu.getMotSecret().charAt(0)));
        dto.setLongueurMot(jeu.getMotSecret().length());
        dto.setTentativesMax(jeu.getTentativesMax());
        dto.setTentativesEffectuees(jeu.getTentatives().size());
        dto.setTentativesRestantes(jeu.getTentativesMax() - jeu.getTentatives().size());
        dto.setDateDebut(jeu.getDateDebut());
        if (jeu.getStatut() != StatutJeu.EN_COURS) dto.setMotSecret(jeu.getMotSecret());
        dto.setMessage(buildMessage(jeu));
        dto.setTentatives(buildTentativeDtos(jeu));
        return dto;
    }

    private List<TentativeDto> buildTentativeDtos(Jeu jeu) {
        return jeu.getTentatives().stream().map(t -> {
            String[] fb = t.getFeedback().split(",");
            String word = t.getMotPropose();
            List<LettreResultat> lettres = IntStream
                    .range(0, word.length())
                    .mapToObj(i -> new LettreResultat(String.valueOf(word.charAt(i)), fb[i]))
                    .collect(Collectors.toList());
            return new TentativeDto(t.getNumero(), word, lettres);
        }).collect(Collectors.toList());
    }

    private String buildMessage(Jeu jeu) {
        return switch (jeu.getStatut()) {
            case GAGNE -> "Mot trouvé en " + jeu.getTentatives().size() + " essai(s) !";
            case PERDU -> "Le mot était : " + jeu.getMotSecret();
            default    -> jeu.getTentativesMax() - jeu.getTentatives().size() + " essai(s) restant(s).";
        };
    }

    // ── Historique (fire-and-forget) ──────────────────────────────────────────

    private void enregistrerHistorique(Jeu jeu, boolean gagne) {
        Map<String, Object> body = new HashMap<>();
        body.put("joueurId",         jeu.getJoueurId());
        body.put("motSecret",        jeu.getMotSecret());
        body.put("nombreTentatives", jeu.getTentatives().size());
        body.put("gagne",            gagne);
        historyClient.enregistrer(body);
    }
}
