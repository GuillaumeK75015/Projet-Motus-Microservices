package com.dauphine.miage.motus_game_service.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Client dédié aux appels vers le dictionary-service.
 * Maintient un cache local en mémoire pour éviter un appel HTTP à chaque guess,
 * et pour servir de repli (fallback) quand le dictionary-service est indisponible.
 */
@Component
public class DictionaryClient {

    private static final Logger log = LoggerFactory.getLogger(DictionaryClient.class);

    @Autowired
    private RestTemplate restTemplate;

    // Référence à soi-même via le proxy Spring : @CircuitBreaker/@Retry ne s'appliquent que sur des
    // appels entrants par le proxy, pas sur un appel "this.xxx()" — nécessaire ici car charger() est
    // invoqué en interne (au démarrage, et en rechargement paresseux depuis motExiste()).
    @Autowired
    @Lazy
    private DictionaryClient self;

    @Value("${services.dictionary.url}")
    private String dictionaryServiceUrl;

    private final Set<String> cache = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // true seulement après un chargement complet réussi du dictionnaire.
    // NB : on ne peut pas se fier à cache.isEmpty() pour décider de recharger, car
    // ajouterAuCache(motSecret) y insère le mot secret au démarrage de chaque partie.
    private volatile boolean chargeComplet = false;

    // ApplicationReadyEvent plutôt que @PostConstruct : à ce stade le bean est pleinement
    // enregistré dans le contexte, donc le self-proxy @Lazy se résout sans cycle de création.
    @EventListener(ApplicationReadyEvent.class)
    public void charger() {
        self.chargerDepuisDictionnaire();
    }

    @CircuitBreaker(name = "dictionary-service", fallbackMethod = "fallbackCharger")
    @Retry(name = "dictionary-service")
    public void chargerDepuisDictionnaire() {
        String[] mots = restTemplate.getForObject(
                dictionaryServiceUrl + "/api/dictionary", String[].class);
        if (mots != null) {
            Arrays.stream(mots).map(String::toUpperCase).forEach(cache::add);
            chargeComplet = true;
            log.info("Dictionnaire chargé en cache : {} mots (7-10 lettres)", cache.size());
        }
    }

    private void fallbackCharger(Throwable t) {
        log.warn("dictionary-service indisponible ({}) — rechargement paresseux activé", t.getMessage());
    }

    /**
     * Tire un mot aléatoire d'une longueur précise (7-10).
     * Utilisé au démarrage de chaque partie Motus. En cas de panne du dictionnaire,
     * le fallback tire un mot du cache local plutôt que d'échouer (dégradation gracieuse).
     */
    @CircuitBreaker(name = "dictionary-service", fallbackMethod = "fallbackMotAleatoire")
    @Retry(name = "dictionary-service")
    public String getMotAleatoireParLongueur(int nombreLettres) {
        return restTemplate.getForObject(
                dictionaryServiceUrl + "/api/dictionary/random/" + nombreLettres, String.class);
    }

    private String fallbackMotAleatoire(int nombreLettres, Throwable t) {
        log.warn("dictionary-service indisponible ({}) — repli sur le cache local pour {} lettres",
                t.getMessage(), nombreLettres);
        return motAleatoireDepuisCache(nombreLettres);
    }

    /** Repli : tire un mot de la longueur demandée dans le cache local (null si le cache est vide). */
    private String motAleatoireDepuisCache(int nombreLettres) {
        if (!chargeComplet) charger();
        List<String> candidats = cache.stream()
                .filter(m -> m.length() == nombreLettres)
                .collect(Collectors.toList());
        if (candidats.isEmpty()) return null;
        return candidats.get(ThreadLocalRandom.current().nextInt(candidats.size()));
    }

    /**
     * Vérifie qu'un mot appartient au dictionnaire.
     * S'appuie sur le cache local (rapide) ; si le chargement complet n'a jamais abouti,
     * on retente d'abord de le charger, puis on interroge le service en dernier recours —
     * ainsi une panne de dictionary-service au démarrage ne bloque pas définitivement le jeu.
     */
    public boolean motExiste(String mot) {
        String upper = mot.toUpperCase();
        if (!chargeComplet) charger();
        if (cache.contains(upper)) return true;
        if (chargeComplet) return false;          // cache fiable et complet → réponse définitive
        return motExisteDistant(upper);           // cache incomplet → vérification autoritaire
    }

    /** Vérification autoritaire auprès de dictionary-service (repli si le cache est incomplet). */
    private boolean motExisteDistant(String mot) {
        try {
            Boolean existe = restTemplate.getForObject(
                    dictionaryServiceUrl + "/api/dictionary/exists/" + mot, Boolean.class);
            return Boolean.TRUE.equals(existe);
        } catch (Exception e) {
            return false;
        }
    }

    public void ajouterAuCache(String mot) {
        cache.add(mot.toUpperCase());
    }
}
