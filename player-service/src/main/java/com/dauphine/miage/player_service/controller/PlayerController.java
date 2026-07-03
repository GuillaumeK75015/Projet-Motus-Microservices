package com.dauphine.miage.player_service.controller;

import com.dauphine.miage.player_service.domain.Joueur;
import com.dauphine.miage.player_service.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Random;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Enregistrement d'un joueur : POST http://localhost:8081/api/players
    @PostMapping
    public ResponseEntity<?> registerPlayer(@RequestBody Joueur joueur) {
        if (playerRepository.findByPseudo(joueur.getPseudo()).isPresent()) {
            return ResponseEntity.badRequest().body("Pseudo déjà utilisé !");
        }
        if (joueur.getPassword() == null || joueur.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body("Mot de passe requis !");
        }
        joueur.setPassword(passwordEncoder.encode(joueur.getPassword()));
        joueur.setRole("ROLE_USER"); // un client ne peut pas s'auto-attribuer ROLE_ADMIN via l'inscription
        try {
            Joueur savedJoueur = playerRepository.save(joueur);
            return new ResponseEntity<>(savedJoueur, HttpStatus.CREATED);
        } catch (DataIntegrityViolationException e) {
            // Deux inscriptions concurrentes avec le même pseudo — la contrainte unique en base tranche.
            return ResponseEntity.badRequest().body("Pseudo déjà utilisé !");
        }
    }

    // Partie en invité, sans compte : POST http://localhost:8081/api/players/guest
    @PostMapping("/guest")
    public ResponseEntity<Joueur> registerGuest() {
        String pseudo;
        Random random = new Random();
        do {
            pseudo = "Invite" + (1000 + random.nextInt(9000));
        } while (playerRepository.findByPseudo(pseudo).isPresent());

        Joueur guest = new Joueur();
        guest.setPseudo(pseudo);
        guest.setEmail("invite@motus.local");
        guest.setRole("ROLE_USER");
        // Pas de mot de passe : identité éphémère, non ré-authentifiable.
        return new ResponseEntity<>(playerRepository.save(guest), HttpStatus.CREATED);
    }

    // Récupération par ID : GET http://localhost:8081/api/players/1
    // Réponse enrichie de liens HATEOAS (self, collection) pour se rapprocher du niveau 3 de Richardson.
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Joueur>> getPlayerById(@PathVariable Long id) {
        return playerRepository.findById(id)
                .map(joueur -> EntityModel.of(joueur,
                        linkTo(methodOn(PlayerController.class).getPlayerById(id)).withSelfRel(),
                        linkTo(methodOn(PlayerController.class).getAllPlayers()).withRel("players")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Recherche par pseudo : GET http://localhost:8081/api/players/pseudo/Guillaume
    @GetMapping("/pseudo/{pseudo}")
    public ResponseEntity<Joueur> getPlayerByPseudo(@PathVariable String pseudo) {
        return playerRepository.findByPseudo(pseudo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Tous les joueurs : GET http://localhost:8081/api/players
    @GetMapping
    public List<Joueur> getAllPlayers() {
        return playerRepository.findAll();
    }

    // Suppression : DELETE http://localhost:8081/api/players/1
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlayer(@PathVariable Long id) {
        if (!playerRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        playerRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}