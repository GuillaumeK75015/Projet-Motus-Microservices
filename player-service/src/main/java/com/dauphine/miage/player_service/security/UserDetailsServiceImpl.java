package com.dauphine.miage.player_service.security;

import com.dauphine.miage.player_service.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String pseudo) throws UsernameNotFoundException {
        var joueur = playerRepository.findByPseudo(pseudo)
                .orElseThrow(() -> new UsernameNotFoundException("Joueur introuvable : " + pseudo));

        // Comptes invités : pas de mot de passe → hash aléatoire (jamais authentifiable),
        // pour éviter de casser BCrypt.matches() sur une valeur vide/non hashée.
        String hash = joueur.getPassword() != null
                ? joueur.getPassword()
                : passwordEncoder.encode(UUID.randomUUID().toString());

        return new User(
                joueur.getPseudo(),
                hash,
                List.of(new SimpleGrantedAuthority(joueur.getRole())));
    }
}
