package com.dauphine.miage.player_service.controller;

import com.dauphine.miage.player_service.domain.Joueur;
import com.dauphine.miage.player_service.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    @Autowired
    private PlayerRepository playerRepository;

    // Enregistrement d'un joueur : POST http://localhost:8081/api/players
    @PostMapping
    public ResponseEntity<?> registerPlayer(@RequestBody Joueur joueur) {
        if (playerRepository.findByPseudo(joueur.getPseudo()).isPresent()) {
            return ResponseEntity.badRequest().body("Pseudo déjà utilisé !");
        }
        Joueur savedJoueur = playerRepository.save(joueur);
        return new ResponseEntity<>(savedJoueur, HttpStatus.CREATED);
    }

    // Récupération par ID : GET http://localhost:8081/api/players/1
    @GetMapping("/{id}")
    public ResponseEntity<Joueur> getPlayerById(@PathVariable Long id) {
        return playerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}