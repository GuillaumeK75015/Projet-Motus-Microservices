package com.dauphine.miage.player_service.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "players")
public class Joueur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String pseudo;

    @Column(nullable = false)
    private String email;

    // WRITE_ONLY : acceptée en entrée (inscription/login) mais jamais renvoyée dans les réponses JSON.
    // (@JsonIgnore sur le champ bloquerait aussi la désérialisation, pas seulement la sérialisation.)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = true)
    private String password;

    /**
     * Rôle Spring Security : "ROLE_ADMIN" ou "ROLE_USER".
     * Utilisé tel quel comme GrantedAuthority dans le JWT et UserDetails.
     */
    @Column(nullable = false)
    private String role = "ROLE_USER";

    private LocalDateTime dateInscription;

    public Joueur() {
        this.dateInscription = LocalDateTime.now();
    }

    public Long getId()                           { return id; }
    public void setId(Long id)                    { this.id = id; }

    public String getPseudo()                     { return pseudo; }
    public void setPseudo(String pseudo)          { this.pseudo = pseudo; }

    public String getEmail()                      { return email; }
    public void setEmail(String email)            { this.email = email; }

    public String getPassword()                   { return password; }
    public void setPassword(String password)      { this.password = password; }

    public String getRole()                       { return role; }
    public void setRole(String role)              { this.role = role; }

    public LocalDateTime getDateInscription()           { return dateInscription; }
    public void setDateInscription(LocalDateTime v)     { this.dateInscription = v; }
}
