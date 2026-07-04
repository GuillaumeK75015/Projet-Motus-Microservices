package com.dauphine.miage.motus_game_service.controller;

import com.dauphine.miage.motus_game_service.domain.StatutJeu;
import com.dauphine.miage.motus_game_service.dto.GameStateDto;
import com.dauphine.miage.motus_game_service.dto.GuessRequest;
import com.dauphine.miage.motus_game_service.dto.StartGameRequest;
import com.dauphine.miage.motus_game_service.service.MotusGameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/games")
public class MotusGameController {

    @Autowired
    private MotusGameService motusGameService;

    // POST http://localhost:8084/api/games/start
    // Body : { "joueurId": 1, "nombreLettres": 8 }  (nombreLettres : 7-10, aléatoire si omis)
    @PostMapping("/start")
    public ResponseEntity<GameStateDto> startGame(@RequestBody StartGameRequest request) {
        GameStateDto dto = motusGameService.startGame(request.getJoueurId(), request.getNombreLettres());
        return ResponseEntity.status(HttpStatus.CREATED).body(addLinks(dto));
    }

    // POST http://localhost:8084/api/games/1/guess
    @PostMapping("/{id}/guess")
    public ResponseEntity<GameStateDto> guess(
            @PathVariable Long id,
            @RequestBody GuessRequest request) {
        return ResponseEntity.ok(addLinks(motusGameService.guess(id, request.getMot())));
    }

    // POST http://localhost:8084/api/games/1/abandon
    @PostMapping("/{id}/abandon")
    public ResponseEntity<GameStateDto> abandon(@PathVariable Long id) {
        return ResponseEntity.ok(addLinks(motusGameService.abandon(id)));
    }

    // GET http://localhost:8084/api/games/1
    @GetMapping("/{id}")
    public ResponseEntity<GameStateDto> getGame(@PathVariable Long id) {
        return ResponseEntity.ok(addLinks(motusGameService.getGame(id)));
    }

    // GET http://localhost:8084/api/games/player/1
    @GetMapping("/player/{joueurId}")
    public List<GameStateDto> getGamesByPlayer(@PathVariable Long joueurId) {
        return motusGameService.getGamesByPlayer(joueurId);
    }

    // GET http://localhost:8084/api/games
    @GetMapping
    public List<GameStateDto> getAllGames() {
        return motusGameService.getAllGames();
    }

    // Liens HATEOAS : self toujours, "guess" seulement si la partie est encore jouable.
    private GameStateDto addLinks(GameStateDto dto) {
        dto.add(linkTo(methodOn(MotusGameController.class).getGame(dto.getId())).withSelfRel());
        if (StatutJeu.EN_COURS.name().equals(dto.getStatut())) {
            dto.add(linkTo(methodOn(MotusGameController.class).guess(dto.getId(), null)).withRel("guess"));
            dto.add(linkTo(methodOn(MotusGameController.class).abandon(dto.getId())).withRel("abandon"));
        }
        dto.add(linkTo(methodOn(MotusGameController.class).getGamesByPlayer(dto.getJoueurId())).withRel("gamesByPlayer"));
        return dto;
    }
}
