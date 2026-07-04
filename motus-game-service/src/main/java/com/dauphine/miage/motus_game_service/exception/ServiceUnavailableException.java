package com.dauphine.miage.motus_game_service.exception;

/**
 * Un service interne dont dépend le jeu (dictionnaire, player-service…) est
 * injoignable ou n'a pas pu répondre. Ce n'est pas une erreur du client.
 * Mappée en HTTP 503 par {@link GlobalExceptionHandler}.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
