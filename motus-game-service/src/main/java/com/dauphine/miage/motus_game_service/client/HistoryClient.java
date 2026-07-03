package com.dauphine.miage.motus_game_service.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Client dédié à l'enregistrement de l'historique (fire-and-forget) : une partie
 * terminée ne doit jamais échouer côté joueur à cause d'une panne de history-stat-service.
 */
@Component
public class HistoryClient {

    private static final Logger log = LoggerFactory.getLogger(HistoryClient.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${services.history.url}")
    private String historyServiceUrl;

    @CircuitBreaker(name = "history-service", fallbackMethod = "fallbackEnregistrer")
    @Retry(name = "history-service")
    public void enregistrer(Map<String, Object> body) {
        restTemplate.postForObject(historyServiceUrl + "/api/history", body, Object.class);
    }

    private void fallbackEnregistrer(Map<String, Object> body, Throwable t) {
        log.warn("Impossible d'enregistrer l'historique (service indisponible) : {}", t.getMessage());
    }
}
