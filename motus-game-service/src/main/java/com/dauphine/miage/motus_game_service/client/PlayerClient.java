package com.dauphine.miage.motus_game_service.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Client dédié aux appels vers player-service (vérification d'existence d'un joueur).
 * Un 404 est une réponse métier normale (joueur inconnu) : elle n'ouvre pas le circuit,
 * seule une panne réseau/timeout déclenche retry puis circuit breaker.
 */
@Component
public class PlayerClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${services.player.url}")
    private String playerServiceUrl;

    @CircuitBreaker(name = "player-service", fallbackMethod = "fallbackExiste")
    @Retry(name = "player-service")
    public boolean existe(Long joueurId) {
        try {
            restTemplate.getForObject(playerServiceUrl + "/api/players/" + joueurId, Object.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    private boolean fallbackExiste(Long joueurId, Throwable t) {
        throw new RuntimeException("Service joueur temporairement indisponible, réessayez plus tard.");
    }
}
