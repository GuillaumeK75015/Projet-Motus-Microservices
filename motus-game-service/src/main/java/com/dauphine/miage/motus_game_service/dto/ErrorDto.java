package com.dauphine.miage.motus_game_service.dto;

import java.time.LocalDateTime;

public class ErrorDto {

    private String message;
    private LocalDateTime timestamp;

    public ErrorDto(String message) {
        this.message   = message;
        this.timestamp = LocalDateTime.now();
    }

    public String getMessage()           { return message; }
    public LocalDateTime getTimestamp()  { return timestamp; }
}
