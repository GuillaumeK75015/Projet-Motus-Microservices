package com.dauphine.miage.motus_game_service.exception;

/**
 * Ressource métier introuvable (joueur, partie…).
 * Mappée en HTTP 404 par {@link GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
