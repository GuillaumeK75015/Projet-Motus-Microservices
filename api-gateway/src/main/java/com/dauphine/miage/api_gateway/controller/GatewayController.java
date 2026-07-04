package com.dauphine.miage.api_gateway.controller;

import com.dauphine.miage.api_gateway.client.DownstreamClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Point d'entrée unique (API Gateway) de l'application : routage vers les microservices
 * internes, relai de l'en-tête Authorization, et enrichissement de réponses (pseudo joueur).
 * Sépare la façade réseau/routage de la logique métier, qui reste dans chaque service.
 */
@RestController
public class GatewayController {

    @Autowired
    private DownstreamClient downstreamClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${services.player.url}")
    private String playerServiceUrl;

    @Value("${services.history.url}")
    private String historyServiceUrl;

    @Value("${services.game.url}")
    private String gameServiceUrl;

    // ── Auth proxy (admin) ────────────────────────────────────────────────────

    @PostMapping("/api/proxy/auth/login")
    public ResponseEntity<String> login(@RequestBody String body) {
        return forwardPlayer(HttpMethod.POST, playerServiceUrl + "/api/auth/login", body, null);
    }

    // ── Player proxy ──────────────────────────────────────────────────────────

    @PostMapping("/api/proxy/players")
    public ResponseEntity<String> registerPlayer(@RequestBody String body) {
        return forwardPlayer(HttpMethod.POST, playerServiceUrl + "/api/players", body, null);
    }

    @PostMapping("/api/proxy/players/guest")
    public ResponseEntity<String> registerGuest() {
        return forwardPlayer(HttpMethod.POST, playerServiceUrl + "/api/players/guest", null, null);
    }

    @GetMapping("/api/proxy/players/{id}")
    public ResponseEntity<String> getPlayerById(@PathVariable Long id) {
        return forwardPlayer(HttpMethod.GET, playerServiceUrl + "/api/players/" + id, null, null);
    }

    @GetMapping("/api/proxy/players/pseudo/{pseudo}")
    public ResponseEntity<String> getPlayerByPseudo(
            @PathVariable String pseudo,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return forwardPlayer(HttpMethod.GET, playerServiceUrl + "/api/players/pseudo/" + pseudo, null, authorization);
    }

    @GetMapping("/api/proxy/players")
    public ResponseEntity<String> getAllPlayers(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return forwardPlayer(HttpMethod.GET, playerServiceUrl + "/api/players", null, authorization);
    }

    @DeleteMapping("/api/proxy/players/{id}")
    public ResponseEntity<String> deletePlayer(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<String> historyResult =
                forwardHistory(HttpMethod.DELETE, historyServiceUrl + "/api/history/player/" + id, null, authorization);
        if (historyResult.getStatusCode() == HttpStatus.UNAUTHORIZED
                || historyResult.getStatusCode() == HttpStatus.FORBIDDEN) {
            return historyResult;
        }
        return forwardPlayer(HttpMethod.DELETE, playerServiceUrl + "/api/players/" + id, null, authorization);
    }

    // ── Stats / Admin proxy ───────────────────────────────────────────────────

    @GetMapping("/api/proxy/stats/{joueurId}")
    public ResponseEntity<String> getStats(@PathVariable Long joueurId) {
        return forwardHistory(HttpMethod.GET, historyServiceUrl + "/api/history/stats/" + joueurId, null, null);
    }

    /**
     * Historique d'un joueur (filtrable par date / résultat / mot) — réservé à ses propres
     * parties, contrairement à {@link #searchParties} qui reste ADMIN uniquement.
     * GET /api/proxy/history/1?date=2026-06-29&gagne=true&mot=motus
     */
    @GetMapping("/api/proxy/history/{joueurId}")
    public ResponseEntity<String> getHistoryByPlayer(
            @PathVariable Long joueurId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Boolean gagne,
            @RequestParam(required = false) String mot) {
        StringBuilder url = new StringBuilder(historyServiceUrl + "/api/history/player/" + joueurId + "?");
        if (date  != null) url.append("date=").append(date).append("&");
        if (gagne != null) url.append("gagne=").append(gagne).append("&");
        if (mot   != null) url.append("mot=").append(URLEncoder.encode(mot, StandardCharsets.UTF_8));
        return forwardHistory(HttpMethod.GET, url.toString(), null, null);
    }

    @GetMapping("/api/proxy/classement")
    public ResponseEntity<String> getClassement() {
        ResponseEntity<String> r = forwardHistory(HttpMethod.GET, historyServiceUrl + "/api/history/classement", null, null);
        return enrichWithPseudo(r, "joueurId", null);
    }

    @GetMapping("/api/proxy/search")
    public ResponseEntity<String> searchParties(
            @RequestParam(required = false) Long joueurId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Boolean gagne,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StringBuilder url = new StringBuilder(historyServiceUrl + "/api/history/search?");
        if (joueurId != null) url.append("joueurId=").append(joueurId).append("&");
        if (date     != null) url.append("date=").append(date).append("&");
        if (gagne    != null) url.append("gagne=").append(gagne);
        ResponseEntity<String> r = forwardHistory(HttpMethod.GET, url.toString(), null, authorization);
        return enrichWithPseudo(r, "joueurId", authorization);
    }

    // ── Jeu (passthrough générique vers motus-game-service) ───────────────────

    @RequestMapping("/api/games/**")
    public ResponseEntity<String> forwardGame(HttpServletRequest request,
                                               @RequestBody(required = false) String body) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String url = gameServiceUrl + path + (query != null ? "?" + query : "");
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpEntity<?> entity = buildEntity(body, request.getHeader(HttpHeaders.AUTHORIZATION));
        return downstreamClient.forwardToGame(method, url, entity);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<String> forwardPlayer(HttpMethod method, String url, Object body, String authorization) {
        return downstreamClient.forwardToPlayer(method, url, buildEntity(body, authorization));
    }

    private ResponseEntity<String> forwardHistory(HttpMethod method, String url, Object body, String authorization) {
        return downstreamClient.forwardToHistory(method, url, buildEntity(body, authorization));
    }

    private HttpEntity<?> buildEntity(Object body, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        if (authorization != null) headers.set(HttpHeaders.AUTHORIZATION, authorization);
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /**
     * Ajoute un champ "pseudoJoueur" à chaque élément d'une liste JSON en résolvant joueurId → pseudo.
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
            return response;
        }
    }

    private Map<Long, String> fetchAllPseudos(String authorization) {
        Map<Long, String> map = new HashMap<>();
        try {
            ResponseEntity<String> r = forwardPlayer(HttpMethod.GET, playerServiceUrl + "/api/players", null, authorization);
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
        }
        return map;
    }

    private String fetchPseudo(Long joueurId) {
        try {
            ResponseEntity<String> r = forwardPlayer(HttpMethod.GET, playerServiceUrl + "/api/players/" + joueurId, null, null);
            if (!r.getStatusCode().is2xxSuccessful() || r.getBody() == null) return "Joueur #" + joueurId;
            Map<?, ?> player = objectMapper.readValue(r.getBody(), Map.class);
            Object pseudo = player.get("pseudo");
            return pseudo != null ? pseudo.toString() : ("Joueur #" + joueurId);
        } catch (Exception e) {
            return "Joueur #" + joueurId;
        }
    }
}
