package com.dauphine.miage.api_gateway.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * Point d'appel unique vers chaque service aval, un bean/méthode par service pour que
 * le nom du circuit breaker Resilience4j (statique, par annotation) corresponde à une
 * instance de config distincte (voir application.properties).
 * Les erreurs HTTP métier (4xx/5xx renvoyées par le service aval) sont volontairement
 * absorbées ici et ne comptent pas comme un échec réseau : seules les pannes de
 * connexion/timeout déclenchent retry puis ouverture du circuit (fallback).
 */
@Component
public class DownstreamClient {

    @Autowired
    private RestTemplate restTemplate;

    @CircuitBreaker(name = "player-service", fallbackMethod = "fallback")
    @Retry(name = "player-service")
    public ResponseEntity<String> forwardToPlayer(HttpMethod method, String url, HttpEntity<?> entity) {
        return doExchange(method, url, entity);
    }

    @CircuitBreaker(name = "history-service", fallbackMethod = "fallback")
    @Retry(name = "history-service")
    public ResponseEntity<String> forwardToHistory(HttpMethod method, String url, HttpEntity<?> entity) {
        return doExchange(method, url, entity);
    }

    @CircuitBreaker(name = "game-service", fallbackMethod = "fallback")
    @Retry(name = "game-service")
    public ResponseEntity<String> forwardToGame(HttpMethod method, String url, HttpEntity<?> entity) {
        return doExchange(method, url, entity);
    }

    private ResponseEntity<String> doExchange(HttpMethod method, String url, HttpEntity<?> entity) {
        try {
            return restTemplate.exchange(url, method, entity, String.class);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // Signature = méthode d'origine + Throwable ; appelée uniquement pour les pannes réseau
    // (timeout, connexion refusée) une fois les tentatives de retry épuisées, ou circuit ouvert.
    private ResponseEntity<String> fallback(HttpMethod method, String url, HttpEntity<?> entity, Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"error\":\"Service temporairement indisponible, réessayez plus tard.\"}");
    }
}
