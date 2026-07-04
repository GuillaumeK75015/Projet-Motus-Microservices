package com.dauphine.miage.player_service.config;

import com.dauphine.miage.player_service.domain.Joueur;
import com.dauphine.miage.player_service.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crée quatre comptes de démonstration (Adam, Lisa, Marie, Baptiste) au démarrage, avec un
 * historique de parties déjà joué (gagnées en x essais, perdues en 6 essais max) pour que le
 * classement et les stats aient tout de suite des données représentatives à montrer.
 *
 * Ne s'exécute que pour les joueurs qui n'existent pas encore (idempotent, comme
 * {@link DataInitializer} pour l'admin) : l'historique n'est seedé qu'au moment de la création
 * du joueur, jamais rejoué aux démarrages suivants.
 */
@Component
public class DemoPlayersInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoPlayersInitializer.class);

    private record PartieDemo(String motSecret, int nombreTentatives, boolean gagne) {}

    private static final List<PartieDemo> PARTIES_ADAM = List.of(
            new PartieDemo("VOITURE", 2, true),
            new PartieDemo("MONTAGNE", 3, true),
            new PartieDemo("CHOCOLAT", 3, true),
            new PartieDemo("TELEPHONE", 4, true),
            new PartieDemo("BICYCLETTE", 5, true),
            new PartieDemo("GRENOUILLE", 6, false)
    );

    private static final List<PartieDemo> PARTIES_MARIE = List.of(
            new PartieDemo("FENETRE", 1, true),
            new PartieDemo("PAPILLON", 2, true),
            new PartieDemo("AVENTURE", 4, true),
            new PartieDemo("CHAUSSURE", 5, true)
    );

    private static final List<PartieDemo> PARTIES_LISA = List.of(
            new PartieDemo("ELEPHANT", 3, true),
            new PartieDemo("PARAPLUIE", 4, true),
            new PartieDemo("ORDINATEUR", 6, true),
            new PartieDemo("BOUTEILLE", 6, false),
            new PartieDemo("MONTAGNE", 6, false)
    );

    private static final List<PartieDemo> PARTIES_BAPTISTE = List.of(
            new PartieDemo("VOITURE", 6, true),
            new PartieDemo("CHOCOLAT", 6, false),
            new PartieDemo("TELEPHONE", 6, false),
            new PartieDemo("AVENTURE", 6, false),
            new PartieDemo("FENETRE", 6, false)
    );

    private record ProfilDemo(String pseudo, List<PartieDemo> parties) {}

    private static final List<ProfilDemo> PROFILS = List.of(
            new ProfilDemo("Adam", PARTIES_ADAM),
            new ProfilDemo("Lisa", PARTIES_LISA),
            new ProfilDemo("Marie", PARTIES_MARIE),
            new ProfilDemo("Baptiste", PARTIES_BAPTISTE)
    );

    @Autowired private PlayerRepository playerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RestTemplate restTemplate;

    @Value("${demo.players.password}")
    private String demoPassword;

    @Value("${services.history.url}")
    private String historyServiceUrl;

    @Override
    public void run(String... args) {
        List<Map.Entry<Long, ProfilDemo>> nouveauxJoueurs = new ArrayList<>();

        for (ProfilDemo profil : PROFILS) {
            if (playerRepository.findByPseudo(profil.pseudo()).isPresent()) {
                continue;
            }

            Joueur joueur = new Joueur();
            joueur.setPseudo(profil.pseudo());
            joueur.setEmail(profil.pseudo().toLowerCase() + "@gmail.com");
            joueur.setPassword(passwordEncoder.encode(demoPassword));
            joueur.setRole("ROLE_USER");
            Long joueurId = playerRepository.save(joueur).getId();

            log.info("Compte de démo créé : {} (id={})", profil.pseudo(), joueurId);
            nouveauxJoueurs.add(Map.entry(joueurId, profil));
        }

        if (nouveauxJoueurs.isEmpty()) {
            return;
        }

        // En tâche de fond : history-stat-service peut démarrer après player-service (aucune
        // dépendance d'ordre entre les deux pods), le seed d'historique ne doit donc jamais
        // bloquer ni retarder le démarrage de player-service lui-même.
        Thread seedThread = new Thread(() -> nouveauxJoueurs.forEach(
                e -> seedHistorique(e.getKey(), e.getValue())), "demo-history-seed");
        seedThread.setDaemon(true);
        seedThread.start();
    }

    private void seedHistorique(Long joueurId, ProfilDemo profil) {
        for (PartieDemo partie : profil.parties()) {
            Map<String, Object> body = new HashMap<>();
            body.put("joueurId", joueurId);
            body.put("motSecret", partie.motSecret());
            body.put("nombreTentatives", partie.nombreTentatives());
            body.put("gagne", partie.gagne());

            boolean enregistre = false;
            for (int essai = 1; essai <= 15 && !enregistre; essai++) {
                try {
                    restTemplate.postForObject(historyServiceUrl + "/api/history", body, Object.class);
                    enregistre = true;
                } catch (Exception e) {
                    if (essai == 15) {
                        log.warn("Historique de démo non enregistré pour {} ({}) : {}",
                                profil.pseudo(), partie.motSecret(), e.getMessage());
                    } else {
                        sleep(2000);
                    }
                }
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
