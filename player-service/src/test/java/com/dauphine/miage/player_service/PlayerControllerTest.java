package com.dauphine.miage.player_service;

import com.dauphine.miage.player_service.controller.PlayerController;
import com.dauphine.miage.player_service.domain.Joueur;
import com.dauphine.miage.player_service.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerControllerTest {

    @Mock
    PlayerRepository playerRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    PlayerController playerController;

    private Joueur joueur;

    @BeforeEach
    void setUp() {
        // linkTo(methodOn(...)) (HATEOAS) a besoin d'une requête HTTP courante, même simulée.
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        joueur = new Joueur();
        joueur.setId(1L);
        joueur.setPseudo("Alice");
        joueur.setEmail("alice@test.fr");
        joueur.setPassword("Password123!");
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
    }

    // ── Enregistrement ────────────────────────────────────────────────────────

    @Test
    void registerPlayer_pseudoDisponible_retourne201() {
        when(playerRepository.findByPseudo("Alice")).thenReturn(Optional.empty());
        when(playerRepository.save(any())).thenReturn(joueur);

        ResponseEntity<?> response = playerController.registerPlayer(joueur);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(joueur);
        verify(playerRepository).save(joueur);
    }

    @Test
    void registerPlayer_pseudoDejaUtilise_retourne400() {
        when(playerRepository.findByPseudo("Alice")).thenReturn(Optional.of(joueur));

        ResponseEntity<?> response = playerController.registerPlayer(joueur);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(playerRepository, never()).save(any());
    }

    @Test
    void registerPlayer_sansMotDePasse_retourne400() {
        joueur.setPassword(null);
        when(playerRepository.findByPseudo("Alice")).thenReturn(Optional.empty());

        ResponseEntity<?> response = playerController.registerPlayer(joueur);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(playerRepository, never()).save(any());
    }

    @Test
    void registerGuest_creeUnJoueurSansMotDePasse() {
        when(playerRepository.findByPseudo(any())).thenReturn(Optional.empty());
        when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Joueur> response = playerController.registerGuest();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getPseudo()).startsWith("Invite");
        assertThat(response.getBody().getPassword()).isNull();
    }

    // ── Récupération par ID ───────────────────────────────────────────────────

    @Test
    void getPlayerById_joueurExistant_retourne200() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(joueur));

        ResponseEntity<EntityModel<Joueur>> response = playerController.getPlayerById(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).isEqualTo(joueur);
        assertThat(response.getBody().getLink("self")).isPresent();
    }

    @Test
    void getPlayerById_joueurInexistant_retourne404() {
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<EntityModel<Joueur>> response = playerController.getPlayerById(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Récupération par pseudo ───────────────────────────────────────────────

    @Test
    void getPlayerByPseudo_pseudoExistant_retourne200() {
        when(playerRepository.findByPseudo("Alice")).thenReturn(Optional.of(joueur));

        ResponseEntity<Joueur> response = playerController.getPlayerByPseudo("Alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPseudo()).isEqualTo("Alice");
    }

    @Test
    void getPlayerByPseudo_pseudoInexistant_retourne404() {
        when(playerRepository.findByPseudo("Inconnu")).thenReturn(Optional.empty());

        ResponseEntity<Joueur> response = playerController.getPlayerByPseudo("Inconnu");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Liste et suppression ──────────────────────────────────────────────────

    @Test
    void getAllPlayers_retourneListe() {
        Joueur j2 = new Joueur();
        j2.setId(2L); j2.setPseudo("Bob");
        when(playerRepository.findAll()).thenReturn(List.of(joueur, j2));

        List<Joueur> liste = playerController.getAllPlayers();

        assertThat(liste).hasSize(2);
    }

    @Test
    void deletePlayer_joueurExistant_retourne200() {
        when(playerRepository.existsById(1L)).thenReturn(true);

        ResponseEntity<Void> response = playerController.deletePlayer(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(playerRepository).deleteById(1L);
    }

    @Test
    void deletePlayer_joueurInexistant_retourne404() {
        when(playerRepository.existsById(99L)).thenReturn(false);

        ResponseEntity<Void> response = playerController.deletePlayer(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(playerRepository, never()).deleteById(any());
    }
}
