package com.dauphine.miage.motus_game_service.exception;

/**
 * Requête invalide côté client : mot mal formé, règle du jeu non respectée,
 * partie déjà terminée, longueur demandée hors bornes…
 * Mappée en HTTP 400 par {@link GlobalExceptionHandler}.
 */
public class InvalidGuessException extends RuntimeException {
    public InvalidGuessException(String message) {
        super(message);
    }
}
