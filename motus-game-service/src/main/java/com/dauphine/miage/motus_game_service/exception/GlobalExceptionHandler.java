package com.dauphine.miage.motus_game_service.exception;

import com.dauphine.miage.motus_game_service.dto.ErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gestion centralisée des erreurs.
 * Remplace les blocs try/catch dans chaque contrôleur et traduit chaque type
 * d'exception métier vers le code HTTP approprié (au lieu d'un 400 systématique).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Ressource introuvable (joueur, partie) → 404
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorDto> handleNotFound(NotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorDto(e.getMessage()));
    }

    // Dépendance interne injoignable (dictionnaire, player-service) → 503
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorDto> handleServiceUnavailable(ServiceUnavailableException e) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorDto(e.getMessage()));
    }

    // Requête client invalide (règle du jeu, mot mal formé, partie terminée) → 400
    @ExceptionHandler({InvalidGuessException.class, RuntimeException.class})
    public ResponseEntity<ErrorDto> handleBadRequest(RuntimeException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorDto("Erreur interne : " + e.getMessage()));
    }
}
