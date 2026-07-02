package com.dauphine.miage.player_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint public dédié aux probes d'infrastructure (Kubernetes readinessProbe, etc.),
 * volontairement découplé des données métier (contrairement à un endpoint comme
 * /api/players/pseudo/{pseudo}, dont les règles d'accès peuvent changer indépendamment).
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
