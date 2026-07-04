package com.dauphine.miage.history_stat_service.controller;

import com.dauphine.miage.history_stat_service.domain.Partie;
import com.dauphine.miage.history_stat_service.dto.ClassementDto;
import com.dauphine.miage.history_stat_service.dto.StatDto;
import com.dauphine.miage.history_stat_service.repository.PartieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    @Autowired
    private PartieRepository partieRepository;

    // ── Écriture ──────────────────────────────────────────────────────────────

    // POST http://localhost:8083/api/history
    @PostMapping
    public ResponseEntity<Partie> savePartie(@RequestBody Partie partie) {
        partie.setDateFin(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(partieRepository.save(partie));
    }

    // ── Lecture joueur ────────────────────────────────────────────────────────

    // GET http://localhost:8083/api/history/player/1
    @GetMapping("/player/{joueurId}")
    public List<Partie> getHistoryByPlayer(@PathVariable Long joueurId) {
        return partieRepository.findByJoueurId(joueurId);
    }

    // Suppression de tout l'historique d'un joueur (appelé lors de la suppression du compte) :
    // DELETE http://localhost:8083/api/history/player/1
    @DeleteMapping("/player/{joueurId}")
    @Transactional
    public ResponseEntity<Void> deleteHistoryByPlayer(@PathVariable Long joueurId) {
        partieRepository.deleteByJoueurId(joueurId);
        return ResponseEntity.ok().build();
    }

    // GET http://localhost:8083/api/history/stats/1
    @GetMapping("/stats/{joueurId}")
    public ResponseEntity<StatDto> getStatsByPlayer(@PathVariable Long joueurId) {
        List<Partie> parties = partieRepository.findByJoueurId(joueurId);
        if (parties.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(buildStat(joueurId, parties));
    }

    // ── Lecture admin ─────────────────────────────────────────────────────────

    // GET http://localhost:8083/api/history
    @GetMapping
    public List<Partie> getAllParties() {
        return partieRepository.findAll();
    }

    /**
     * Recherche multi-critères (tous les paramètres sont optionnels).
     * Le filtrage est délégué à la base (voir {@link PartieRepository#search}) ; le filtre
     * "date" est traduit en intervalle [début de journée, début du lendemain[.
     * GET http://localhost:8083/api/history/search?joueurId=1&date=2026-06-29&gagne=true
     */
    @GetMapping("/search")
    public List<Partie> searchParties(
            @RequestParam(required = false) Long joueurId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Boolean gagne) {

        LocalDateTime debut = (date != null) ? date.atStartOfDay()            : null;
        LocalDateTime fin   = (date != null) ? date.plusDays(1).atStartOfDay() : null;
        return partieRepository.search(joueurId, gagne, debut, fin);
    }

    /**
     * Classement global des joueurs trié par victoires décroissantes.
     * L'agrégation (parties jouées/gagnées par joueur) est faite en base ; seul le tri
     * final et l'attribution du rang restent côté application.
     * GET http://localhost:8083/api/history/classement
     */
    @GetMapping("/classement")
    public List<ClassementDto> getClassement() {
        AtomicInteger rang = new AtomicInteger(1);

        return partieRepository.aggregateClassement().stream()
                .map(row -> {
                    int played = (int) row.getJouees();
                    int wins   = (int) row.getGagnees();
                    double rate = played > 0
                            ? Math.round((double) wins / played * 1000) / 10.0
                            : 0.0;
                    return new ClassementDto(row.getJoueurId(), played, wins, rate);
                })
                .sorted(Comparator
                        .comparingInt(ClassementDto::getPartiesGagnees).reversed()
                        .thenComparing(Comparator.comparingDouble(ClassementDto::getTauxVictoire).reversed()))
                .peek(dto -> dto.setRang(rang.getAndIncrement()))
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StatDto buildStat(Long joueurId, List<Partie> parties) {
        int played = parties.size();
        int wins = (int) parties.stream().filter(Partie::isGagne).count();
        double winRate = Math.round((double) wins / played * 1000) / 10.0;
        double avgAttempts = parties.stream().mapToInt(Partie::getNombreTentatives).average().orElse(0);
        return new StatDto(joueurId, played, wins, winRate, avgAttempts);
    }
}
