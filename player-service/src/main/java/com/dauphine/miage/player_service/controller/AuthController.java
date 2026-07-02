package com.dauphine.miage.player_service.controller;

import com.dauphine.miage.player_service.dto.LoginRequest;
import com.dauphine.miage.player_service.dto.LoginResponse;
import com.dauphine.miage.player_service.repository.PlayerRepository;
import com.dauphine.miage.player_service.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // Connexion (joueur ou admin) : POST http://localhost:8081/api/auth/login  { "pseudo": "...", "password": "..." }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getPseudo(), request.getPassword()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Pseudo ou mot de passe incorrect.");
        }

        var joueur = playerRepository.findByPseudo(request.getPseudo()).orElseThrow();
        String token = jwtUtil.generateToken(joueur);
        return ResponseEntity.ok(new LoginResponse(joueur.getId(), token, joueur.getPseudo(), joueur.getRole()));
    }
}
