package com.dauphine.miage.motus_game_service;

import com.dauphine.miage.motus_game_service.client.DictionaryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryClientTest {

    @Mock
    private RestTemplate restTemplate;

    private DictionaryClient client;

    @BeforeEach
    void setUp() {
        client = new DictionaryClient();
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "dictionaryServiceUrl", "http://dict");
        // Hors contexte Spring, le self-proxy pointe simplement sur l'instance elle-même.
        ReflectionTestUtils.setField(client, "self", client);
    }

    /**
     * Régression : ajouterAuCache(motSecret) insère le mot secret → le cache n'est plus vide,
     * mais ne contient QUE ce mot. motExiste doit malgré tout recharger le dictionnaire complet
     * (indicateur chargeComplet) au lieu de se fier au cache pollué et refuser les mots valides.
     */
    @Test
    void motExiste_cachePollueParMotSecret_rechargeLeDictionnaireComplet() {
        client.ajouterAuCache("SECRETX"); // cache non vide (1 mot), chargeComplet encore false
        when(restTemplate.getForObject(anyString(), eq(String[].class)))
                .thenReturn(new String[]{"MAISONS", "JARDINS"});

        assertThat(client.motExiste("maisons")).isTrue();   // rechargé malgré le cache non vide
        assertThat(client.motExiste("SECRETX")).isTrue();   // le mot secret reste valide
        assertThat(client.motExiste("motbidon")).isFalse(); // absent du dictionnaire
    }

    /**
     * Si le chargement complet n'aboutit jamais (dictionnaire renvoie vide), motExiste interroge
     * l'endpoint /exists en dernier recours plutôt que de refuser un mot valide.
     */
    @Test
    void motExiste_cacheIncomplet_verifieAupresDuService() {
        when(restTemplate.getForObject(anyString(), eq(String[].class))).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(Boolean.class))).thenReturn(Boolean.TRUE);

        assertThat(client.motExiste("plateaux")).isTrue();
    }
}
