package com.dauphine.miage.motus_game_service.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
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
        return forward(HttpMethod.POST, playerServiceUrl + "/api/auth/login", body, null);
    }

    // ── Player proxy ──────────────────────────────────────────────────────────

    @PostMapping("/players")
    public ResponseEntity<String> registerPlayer(@RequestBody String body) {
        return forward(HttpMethod.POST, playerServiceUrl + "/api/players", body, null);
    }

    // Partie en invité, sans compte ni mot de passe
    @PostMapping("/players/guest")
    public ResponseEntity<String> registerGuest() {
        return forward(HttpMethod.POST, playerServiceUrl + "/api/players/guest", null, null);
    }

    @GetMapping("/players/{id}")
    public ResponseEntity<String> getPlayerById(@PathVariable Long id) {
        return forward(HttpMethod.GET, playerServiceUrl + "/api/players/" + id, null, null);
    }

    // Recherche par pseudo — ADMIN uniquement (utilisée par le panneau de recherche admin)
    @GetMapping("/players/pseudo/{pseudo}")
    public ResponseEntity<String> getPlayerByPseudo(
            @PathVariable String pseudo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return forward(HttpMethod.GET, playerServiceUrl + "/api/players/pseudo/" + pseudo, null, authorization);
    }

    // GET admin : liste de tous les joueurs — nécessite un JWT ADMIN (relayé depuis le front)
    @GetMapping("/players")
    public ResponseEntity<String> getAllPlayers(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return forward(HttpMethod.GET, playerServiceUrl + "/api/players", null, authorization);
    }

    // DELETE admin : supprime un joueur ET tout son historique (cascade inter-services)
    @DeleteMapping("/players/{id}")
    public ResponseEntity<String> deletePlayer(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // Historique d'abord : si le joueur n'a jamais joué, history-stat-service répond simplement 200.
        // Une erreur d'autorisation ici (401/403) doit interrompre l'opération avant de toucher au compte.
        ResponseEntity<String> historyResult =
                forward(HttpMethod.DELETE, historyServiceUrl + "/api/history/player/" + id, null, authorization);
        if (historyResult.getStatusCode() == HttpStatus.UNAUTHORIZED
                || historyResult.getStatusCode() == HttpStatus.FORBIDDEN) {
            return historyResult;
        }
        return forward(HttpMethod.DELETE, playerServiceUrl + "/api/players/" + id, null, authorization);
    }

    // ── Stats / Admin proxy ───────────────────────────────────────────────────

    @GetMapping("/stats/{joueurId}")
    public ResponseEntity<String> getStats(@PathVariable Long joueurId) {
        return forward(HttpMethod.GET, historyServiceUrl + "/api/history/stats/" + joueurId, null, null);
    }

    @GetMapping("/classement")
    public ResponseEntity<String> getClassement() {
        ResponseEntity<String> r = forward(HttpMethod.GET, historyServiceUrl + "/api/history/classement", null, null);
        return enrichWithPseudo(r, "joueurId", null);
    }

    // GET admin : recherche multi-critères (sans filtre = toutes les parties) — nécessite un JWT ADMIN
    @GetMapping("/search")
    public ResponseEntity<String> searchParties(
            @RequestParam(required = false) Long joueurId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Boolean gagne,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StringBuilder url = new StringBuilder(historyServiceUrl + "/api/history/search?");
        if (joueurId != null) url.append("joueurId=").append(joueurId).append("&");
        if (date     != null) url.append("date=").append(date).append("&");
        if (gagne    != null) url.append("gagne=").append(gagne);
        ResponseEntity<String> r = forward(HttpMethod.GET, url.toString(), null, authorization);
        return enrichWithPseudo(r, "joueurId", authorization);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Relaie un appel vers un service interne en gérant proprement toutes les erreurs :
     * erreurs HTTP (4xx/5xx) renvoyées telles quelles, service injoignable/timeout → 502.
     */
    private ResponseEntity<String> forward(HttpMethod method, String url, Object body, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        if (authorization != null) headers.set(HttpHeaders.AUTHORIZATION, authorization);
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> r = restTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
            return ResponseEntity.status(r.getStatusCode()).body(r.getBody());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Service temporairement indisponible.");
        }
    }

    /**
     * Ajoute un champ "pseudoJoueur" à chaque élément d'une liste JSON en résolvant joueurId → pseudo.
     * Si un token admin est fourni, un seul appel à player-service (liste complète) alimente le cache
     * et évite un appel HTTP par joueur distinct (N+1) ; sinon (ex: classement, public) on retombe
     * sur une résolution joueur par joueur via l'endpoint public GET /api/players/{id}.
     */
    private ResponseEntity<String> enrichWithPseudo(ResponseEntity<String> response, String idField, String authorization) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return response;
        }
        try {
            List<Map<String, Object>> items = objectMapper.readValue(
                    response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            Map<Long, String> pseudoCache = authorization != null ? fetchAllPseudos(authorization) : new HashMap<>();
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

    private Map<Long, String> fetchAllPseudos(String authorization) {
        Map<Long, String> map = new HashMap<>();
        try {
            ResponseEntity<String> r = forward(HttpMethod.GET, playerServiceUrl + "/api/players", null, authorization);
            if (!r.getStatusCode().is2xxSuccessful() || r.getBody() == null) return map;
            List<Map<String, Object>> players = objectMapper.readValue(
                    r.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> player : players) {
                Object id = player.get("id");
                Object pseudo = player.get("pseudo");
                if (id != null && pseudo != null) {
                    map.put(((Number) id).longValue(), pseudo.toString());
                }
            }
        } catch (Exception ignored) {
            // Table vide en cas d'échec : on retombera sur fetchPseudo() joueur par joueur.
        }
        return map;
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
