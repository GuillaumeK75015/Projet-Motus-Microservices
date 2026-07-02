package com.dauphine.miage.dictionary_service.controller;

import com.dauphine.miage.dictionary_service.service.DictionaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dictionary")
public class DictionaryController {

    private static final Logger adminLog = LoggerFactory.getLogger("ADMIN.dictionary");

    @Autowired
    private DictionaryService dictionaryService;

    // ── Endpoints publics ──────────────────────────────────────────────────────

    /** GET /api/dictionary/random — mot aléatoire (longueur quelconque) */
    @GetMapping("/random")
    public ResponseEntity<String> getRandomWord() {
        return dictionaryService.getRandomWord()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/dictionary/random/{length} — mot aléatoire d'une longueur donnée (7-10).
     * C'est l'endpoint utilisé par motus-game-service au démarrage de chaque partie.
     */
    @GetMapping("/random/{length}")
    public ResponseEntity<String> getRandomWordByLength(@PathVariable int length) {
        if (length < 7 || length > 10) {
            return ResponseEntity.badRequest().build();
        }
        return dictionaryService.getRandomWordByLength(length)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/dictionary — tous les mots valides (7-10 lettres) */
    @GetMapping
    public List<String> getAllWords() {
        return dictionaryService.getAllWords();
    }

    /** GET /api/dictionary/length/{length} — tous les mots d'une longueur donnée */
    @GetMapping("/length/{length}")
    public ResponseEntity<List<String>> getWordsByLength(@PathVariable int length) {
        if (length < 7 || length > 10) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(dictionaryService.getWordsByLength(length));
    }

    /** GET /api/dictionary/exists/{mot} — vérifie si un mot appartient au dictionnaire */
    @GetMapping("/exists/{mot}")
    public ResponseEntity<Boolean> wordExists(@PathVariable String mot) {
        return ResponseEntity.ok(dictionaryService.wordExists(mot));
    }

    // ── Endpoints d'administration ─────────────────────────────────────────────

    /**
     * POST /api/dictionary — ajoute un mot dans le dictionnaire (admin).
     * Le mot est normalisé (accents supprimés, majuscules) et validé (7-10 lettres, pas de tiret…)
     * avant insertion.
     */
    @PostMapping
    public ResponseEntity<String> addWord(@RequestBody String mot) {
        adminLog.info("[ADMIN] Demande d'ajout : {}", mot.trim());
        String added = dictionaryService.addWord(mot.trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(added);
    }

    /**
     * DELETE /api/dictionary/{mot} — supprime un mot du dictionnaire (admin).
     */
    @DeleteMapping("/{mot}")
    public ResponseEntity<Void> removeWord(@PathVariable String mot) {
        adminLog.info("[ADMIN] Demande de suppression : {}", mot);
        return dictionaryService.removeWord(mot)
                ? ResponseEntity.ok().<Void>build()
                : ResponseEntity.notFound().build();
    }

    /**
     * GET /api/dictionary/admin/stats — répartition des mots par longueur (admin).
     * Exemple de réponse : { "7": 12500, "8": 18000, "9": 14000, "10": 9000 }
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<Map<Integer, Integer>> getAdminStats() {
        Map<Integer, Integer> stats = dictionaryService.getStatsByLength();
        adminLog.info("[ADMIN] Consultation des statistiques : {}", stats);
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/dictionary/admin/logs — 200 dernières actions d'administration (admin).
     * Inclut : chargement du dictionnaire, ajouts/suppressions, cycles épuisés.
     */
    @GetMapping("/admin/logs")
    public ResponseEntity<List<String>> getAdminLogs() {
        adminLog.info("[ADMIN] Consultation du journal admin");
        return ResponseEntity.ok(dictionaryService.getJournalAdmin());
    }
}
