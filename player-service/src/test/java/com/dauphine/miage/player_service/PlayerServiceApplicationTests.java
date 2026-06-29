package com.dauphine.miage.player_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import com.dauphine.miage.player_service.controller.PlayerController;
import com.dauphine.miage.player_service.domain.Joueur;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class PlayerServiceApplicationTests {

	@Autowired
	private PlayerController playerController;

	@Test
	public void testCreatePlayerDirectly() {
		// Préparation du joueur avec un pseudo unique
		Joueur joueurRequest = new Joueur();
		joueurRequest.setPseudo("Fahd_" + System.currentTimeMillis()); //Ajoute un code basé sur l'heure d'inscription pr pouvoir tester infiniment
		joueurRequest.setEmail("Fahd@dauphine.eu");

		// Appel de la méthode (on utilise bien le type '?' ici pour correspondre au Controller)
		ResponseEntity<?> response = playerController.registerPlayer(joueurRequest);

		//Vérifications
		assertNotNull(response.getBody());
		assertEquals(201, response.getStatusCode().value());
	}
}