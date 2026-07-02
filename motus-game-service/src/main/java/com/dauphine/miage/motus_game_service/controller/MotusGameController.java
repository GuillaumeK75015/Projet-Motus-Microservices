package com.dauphine.miage.motus_game_service.controller;

import com.dauphine.miage.motus_game_service.dto.GameStateDto;
import com.dauphine.miage.motus_game_service.dto.GuessRequest;
import com.dauphine.miage.motus_game_service.dto.StartGameRequest;
import com.dauphine.miage.motus_game_service.service.MotusGameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // POST http://localhost:8084/api/games/1/guess
    @PostMapping("/{id}/guess")
    public ResponseEntity<GameStateDto> guess(
            @PathVariable Long id,
            @RequestBody GuessRequest request) {
        return ResponseEntity.ok(motusGameService.guess(id, request.getMot()));
    }

    // GET http://localhost:8084/api/games/1
    @GetMapping("/{id}")
    public ResponseEntity<GameStateDto> getGame(@PathVariable Long id) {
        return ResponseEntity.ok(motusGameService.getGame(id));
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
}
