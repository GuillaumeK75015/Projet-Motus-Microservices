package com.dauphine.miage.motus_game_service.client;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client dédié aux appels vers le dictionary-service.
 * Maintient un cache local en mémoire pour éviter un appel HTTP à chaque guess.
 */
@Component
public class DictionaryClient {

    private static final Logger log = LoggerFactory.getLogger(DictionaryClient.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${services.dictionary.url}")
    private String dictionaryServiceUrl;

    private final Set<String> cache = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @PostConstruct
    public void charger() {
        try {
            String[] mots = restTemplate.getForObject(
                    dictionaryServiceUrl + "/api/dictionary", String[].class);
            if (mots != null) {
                Arrays.stream(mots).map(String::toUpperCase).forEach(cache::add);
                log.info("Dictionnaire chargé en cache : {} mots (7-10 lettres)", cache.size());
            }
        } catch (Exception e) {
            log.warn("dictionary-service indisponible au démarrage — rechargement paresseux activé");
        }
    }

    /** Tire un mot aléatoire (longueur quelconque 7-10). */
    public String getMotAleatoire() {
        return restTemplate.getForObject(
                dictionaryServiceUrl + "/api/dictionary/random", String.class);
    }

    /**
     * Tire un mot aléatoire d'une longueur précise (7-10).
     * Utilisé au démarrage de chaque partie Motus.
     */
    public String getMotAleatoireParLongueur(int nombreLettres) {
        return restTemplate.getForObject(
                dictionaryServiceUrl + "/api/dictionary/random/" + nombreLettres, String.class);
    }

    public boolean motExiste(String mot) {
        if (cache.isEmpty()) charger();
        return cache.contains(mot.toUpperCase());
    }

    public void ajouterAuCache(String mot) {
        cache.add(mot.toUpperCase());
    }
}
