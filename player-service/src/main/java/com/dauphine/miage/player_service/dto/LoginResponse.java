package com.dauphine.miage.player_service.dto;

public class LoginResponse {

    private final Long id;
    private final String token;
    private final String pseudo;
    private final String role;

    public LoginResponse(Long id, String token, String pseudo, String role) {
        this.id = id;
        this.token = token;
        this.pseudo = pseudo;
        this.role = role;
    }

    public Long getId()       { return id; }
    public String getToken()  { return token; }
    public String getPseudo() { return pseudo; }
    public String getRole()   { return role; }
}
