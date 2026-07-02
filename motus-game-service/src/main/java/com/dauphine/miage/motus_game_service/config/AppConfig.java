package com.dauphine.miage.motus_game_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000); // 3 s pour établir la connexion
        factory.setReadTimeout(5_000);    // 5 s pour lire la réponse
        return new RestTemplate(factory);
    }

    // Autoconfiguration Jackson non fiable avec spring-boot-starter-webmvc dans ce projet
    // (voir crash ProxyController au démarrage) → bean déclaré explicitement.
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
