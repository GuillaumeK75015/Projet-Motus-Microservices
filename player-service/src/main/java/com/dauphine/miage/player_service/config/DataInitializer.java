package com.dauphine.miage.player_service.config;

import com.dauphine.miage.player_service.domain.Joueur;
import com.dauphine.miage.player_service.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée le compte administrateur au démarrage s'il n'existe pas déjà.
 * Identifiants définis dans application.properties (admin.pseudo / admin.email / admin.password).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${admin.pseudo}")
    private String adminPseudo;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (playerRepository.findByPseudo(adminPseudo).isPresent()) {
            return;
        }
        Joueur admin = new Joueur();
        admin.setPseudo(adminPseudo);
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole("ROLE_ADMIN");
        playerRepository.save(admin);
    }
}
