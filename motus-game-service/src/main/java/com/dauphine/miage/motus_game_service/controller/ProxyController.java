package com.dauphine.miage.motus_game_service.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxy transparent vers player-service et history-stat-service.
 * Permet à l'interface web (servie depuis ce service) d'éviter tout problème CORS.
 */
@RestController
@RequestMapping("/api/proxy")
public class ProxyController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${services.player.url}")
    private String playerServiceUrl;

    @Value("${services.history.url}")
    private String historyServiceUrl;

    // ── Auth proxy (admin) ────────────────────────────────────────────────────

    @PostMapping("/auth/login")
    public ResponseEntity<String> login(@RequestBody String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> r = restTemplate.exchange(
                    playerServiceUrl + "/api/auth/login",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);
            return ResponseEntity.status(r.getStatusCode()).body(r.getBody());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // ── Player proxy ──────────────────────────────────────────────────────────

    @PostMapping("/players")
    public ResponseEntity<String> registerPlayer(@RequestBody String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> r = restTemplate.exchange(
                    playerServiceUrl + "/api/players",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);
            return ResponseEntity.status(r.getStatusCode()).body(r.getBody());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // Partie en invité, sans compte ni mot de passe
    @PostMapping("/players/guest")
    public ResponseEntity<String> registerGuest() {
        try {
            ResponseEntity<String> r = restTemplate.postForEntity(
                    playerServiceUrl + "/api/players/guest", null, String.class);
            return ResponseEntity.status(r.getStatusCode()).body(r.getBody());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @GetMapping("/players/{id}")
    public ResponseEntity<String> getPlayerById(@PathVariable Long id) {
        try {
            ResponseEntity<String> r = restTemplate.getForEntity(
                    playerServiceUrl + "/api/players/" + id, String.class);
            return ResponseEntity.ok(r.getBody());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @GetMapping("/players/pseudo/{pseudo}")
    public ResponseEntity<String> getPlayerByPseudo(@PathVariable String pseudo) {
        try {
            ResponseEntity<String> r = restTemplate.getForEntity(
                    playerServiceUrl + "/api/players/pseudo/" + pseudo, String.class);
            return ResponseEntity.ok(r.getBody());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // GET admin : liste de tous les joueurs — nécessite un JWT ADMIN (relayé depuis le front)
    @GetMapping("/players")
    public ResponseEntity<String> getAllPlayers(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            ResponseEntity<String> r = restTemplate.exchange(
                    playerServiceUrl + "/api/players",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(authorization)),
                    String.class);
            return ResponseEntity.status(r.getStatusCode()).body(r.getBody());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // DELETE admin : supprime un joueur ET tout son historique (cascade inter-services)
    @DeleteMapping("/players/{id}")
    public ResponseEntity<String> deletePlayer(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            // Historique d'abord : si le joueur n'a jamais joué, history-stat-service répond simplement 200.
            restTemplate.exchange(
                    historyServiceUrl + "/api/history/player/" + id,
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(authorization)),
                    Void.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
            }
            // autre erreur (ex: rien à supprimer) → on continue quand même la suppression du compte
        }
        try {
            ResponseEntity<String> r = restTemplate.exchange(
                    playerServiceUrl + "/api/players/" + id,
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(authorization)),
                    String.class);
            return ResponseEntity.status(r.getStatusCode()).body(r.getBody());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // ── Stats / Admin proxy ───────────────────────────────────────────────────

    @GetMapping("/stats/{joueurId}")
    public ResponseEntity<String> getStats(@PathVariable Long joueurId) {
        try {
            ResponseEntity<String> r = restTemplate.getForEntity(
                    historyServiceUrl + "/api/history/stats/" + joueurId, String.class);
            return ResponseEntity.ok(r.getBody());
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @GetMapping("/classement")
    public ResponseEntity<String> getClassement() {
        try {
            ResponseEntity<String> r = restTemplate.getForEntity(
                    historyServiceUrl + "/api/history/classement", String.class);
            return enrichWithPseudo(r, "joueurId");
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // GET admin : liste de toutes les parties — nécessite un JWT ADMIN (relayé depuis le front)
    @GetMapping("/history")
    public ResponseEntity<String> getAllParties(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            ResponseEntity<String> r = restTemplate.exchange(
                    historyServiceUrl + "/api/history",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(authorization)),
                    String.class);
            return enrichWithPseudo(r, "joueurId");
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // GET admin : recherche multi-critères — nécessite un JWT ADMIN (relayé depuis le front)
    @GetMapping("/search")
    public ResponseEntity<String> searchParties(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long joueurId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String date,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean gagne,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StringBuilder url = new StringBuilder(historyServiceUrl + "/api/history/search?");
        if (joueurId != null) url.append("joueurId=").append(joueurId).append("&");
        if (date     != null) url.append("date=").append(date).append("&");
        if (gagne    != null) url.append("gagne=").append(gagne);
        try {
            ResponseEntity<String> r = restTemplate.exchange(
                    url.toString(),
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(authorization)),
                    String.class);
            return enrichWithPseudo(r, "joueurId");
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    private HttpHeaders authHeaders(String authorization) {
        HttpHeaders headers = new HttpHeaders();
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        return headers;
    }

    /**
     * Ajoute un champ "pseudoJoueur" à chaque élément d'une liste JSON en résolvant
     * joueurId → pseudo via player-service (avec cache pour éviter les appels redondants).
     */
    private ResponseEntity<String> enrichWithPseudo(ResponseEntity<String> response, String idField) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        }
        try {
            List<Map<String, Object>> items = objectMapper.readValue(
                    response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            Map<Long, String> pseudoCache = new HashMap<>();
            for (Map<String, Object> item : items) {
                Object idValue = item.get(idField);
                if (idValue == null) continue;
                Long joueurId = ((Number) idValue).longValue();
                item.put("pseudoJoueur", pseudoCache.computeIfAbsent(joueurId, this::fetchPseudo));
            }
            return ResponseEntity.status(response.getStatusCode()).body(objectMapper.writeValueAsString(items));
        } catch (Exception e) {
            // Enrichissement impossible (parsing…) → on renvoie la réponse d'origine plutôt que d'échouer
            return response;
        }
    }

    private String fetchPseudo(Long joueurId) {
        try {
            Map<String, Object> player = restTemplate.getForObject(
                    playerServiceUrl + "/api/players/" + joueurId, Map.class);
            Object pseudo = player != null ? player.get("pseudo") : null;
            return pseudo != null ? pseudo.toString() : ("Joueur #" + joueurId);
        } catch (Exception e) {
            return "Joueur #" + joueurId;
        }
    }
}
