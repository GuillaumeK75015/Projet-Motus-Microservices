package com.dauphine.miage.history_stat_service.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Probes d'infrastructure (Kubernetes, Prometheus) — publiques
                        .requestMatchers("/actuator/**").permitAll()
                        // Écriture des résultats — appelée par motus-game-service, pas d'utilisateur authentifié
                        .requestMatchers(HttpMethod.POST, "/api/history").permitAll()
                        // Historique / stats / classement d'un joueur — publics (consultés par les joueurs)
                        .requestMatchers(HttpMethod.GET, "/api/history/player/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/history/stats/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/history/classement").permitAll()
                        // Liste complète et recherche multi-critères — administration, ADMIN uniquement
                        .requestMatchers(HttpMethod.GET, "/api/history", "/api/history/search").hasRole("ADMIN")
                        // Suppression de l'historique d'un joueur (cascade lors de la suppression du compte) — ADMIN uniquement
                        .requestMatchers(HttpMethod.DELETE, "/api/history/player/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
