package com.dauphine.miage.dictionary_service.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

@Service
public class DictionaryService {

    private static final Logger log      = LoggerFactory.getLogger(DictionaryService.class);
    private static final Logger adminLog = LoggerFactory.getLogger("ADMIN.dictionary");

    private static final int LONGUEUR_MIN = 7;
    private static final int LONGUEUR_MAX = 10;

    // Suffixes de formes verbales conjuguées à exclure explicitement
    private static final Set<String> SUFFIXES_CONJUGUES = Set.of(
        "AIENT", "ERENT", "ASSENT", "ERAIENT", "IRAIENT", "URAIENT",
        "ASSIEZ", "ISSIEZ", "USSIEZ", "ASSIONS", "ISSIONS", "USSIONS",
        "ERONT", "ERONS", "EREZ", "IRONT", "IRONS", "URONT", "URONS"
    );

    // Mots indexés par longueur (7 → 10)
    private final Map<Integer, List<String>> motsByLength = new HashMap<>();

    // Mots déjà tirés (pour éviter les répétitions par longueur)
    private final Map<Integer, Set<String>> motsUtilisesByLength = new HashMap<>();

    private final Random random = new Random();

    // Journal admin (200 dernières entrées)
    private final Deque<String> journalAdmin = new ConcurrentLinkedDeque<>();
    private static final int JOURNAL_MAX = 200;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Chargement ────────────────────────────────────────────────────────────

    @PostConstruct
    public void chargerMots() throws Exception {
        ClassPathResource resource = new ClassPathResource("mots.txt");
        int total = 0, valides = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String ligne;
            while ((ligne = reader.readLine()) != null) {
                total++;
                String original = ligne.trim();
                if (original.isEmpty()) continue;

                // Exclure noms propres (première lettre majuscule dans le fichier source)
                if (Character.isUpperCase(original.charAt(0))) continue;

                String mot = normaliser(original);
                if (estValide(mot)) {
                    motsByLength.computeIfAbsent(mot.length(), k -> new ArrayList<>()).add(mot);
                    valides++;
                }
            }
        }

        Map<Integer, Integer> stats = getStatsByLength();
        String detail = stats.entrySet().stream()
                .map(e -> e.getKey() + " lettres: " + e.getValue())
                .collect(Collectors.joining(", "));

        String msg = String.format("Dictionnaire chargé — %d mots valides sur %d lus (%s)", valides, total, detail);
        adminLog.info("[ADMIN] {}", msg);
        logAdmin(msg);
        log.info("{}", msg);
    }

    // ── Normalisation & validation ─────────────────────────────────────────────

    private String normaliser(String mot) {
        return mot.toUpperCase()
            .replace('É', 'E').replace('È', 'E').replace('Ê', 'E').replace('Ë', 'E')
            .replace('À', 'A').replace('Â', 'A').replace('Ä', 'A')
            .replace('Î', 'I').replace('Ï', 'I')
            .replace('Ô', 'O').replace('Ö', 'O')
            .replace('Ù', 'U').replace('Û', 'U').replace('Ü', 'U')
            .replace('Ç', 'C').replace('Ÿ', 'Y')
            .replaceAll("[^A-Z]", ""); // supprime tirets, apostrophes, chiffres, etc.
    }

    private boolean estValide(String mot) {
        if (mot.length() < LONGUEUR_MIN || mot.length() > LONGUEUR_MAX) return false;
        // Après normaliser(), le mot ne contient que A-Z. Vérification redondante mais explicite.
        if (!mot.matches("[A-Z]+")) return false;
        // Exclure les formes verbales conjuguées trop reconnaissables
        for (String suffixe : SUFFIXES_CONJUGUES) {
            if (mot.endsWith(suffixe)) return false;
        }
        return true;
    }

    // ── Accès au dictionnaire ─────────────────────────────────────────────────

    public Optional<String> getRandomWord() {
        // Choisir une longueur au hasard parmi celles disponibles, puis un mot
        List<Integer> longueurs = new ArrayList<>(motsByLength.keySet());
        if (longueurs.isEmpty()) return Optional.empty();
        int length = longueurs.get(random.nextInt(longueurs.size()));
        return getRandomWordByLength(length);
    }

    public Optional<String> getRandomWordByLength(int length) {
        List<String> mots = motsByLength.getOrDefault(length, Collections.emptyList());
        if (mots.isEmpty()) return Optional.empty();

        Set<String> utilises = motsUtilisesByLength.computeIfAbsent(length, k -> new LinkedHashSet<>());
        List<String> disponibles = mots.stream()
                .filter(m -> !utilises.contains(m))
                .collect(Collectors.toList());

        if (disponibles.isEmpty()) {
            // Tous les mots de cette longueur ont été tirés : on repart à zéro
            String resetMsg = "Cycle terminé pour " + length + " lettres — réinitialisation";
            adminLog.info("[ADMIN] {}", resetMsg);
            logAdmin(resetMsg);
            utilises.clear();
            disponibles = new ArrayList<>(mots);
        }

        String mot = disponibles.get(random.nextInt(disponibles.size()));
        utilises.add(mot);
        return Optional.of(mot);
    }

    public List<String> getAllWords() {
        return motsByLength.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toUnmodifiableList());
    }

    public List<String> getWordsByLength(int length) {
        return Collections.unmodifiableList(
                motsByLength.getOrDefault(length, Collections.emptyList()));
    }

    public boolean wordExists(String mot) {
        String normalized = normaliser(mot);
        return motsByLength.getOrDefault(normalized.length(), Collections.emptyList())
                           .contains(normalized);
    }

    // ── Administration ────────────────────────────────────────────────────────

    public String addWord(String mot) {
        String upper = normaliser(mot.trim());
        if (!estValide(upper)) {
            String msg = "Ajout refusé (mot non valide pour Motus) : " + upper;
            adminLog.warn("[ADMIN] {}", msg);
            logAdmin(msg);
            return upper;
        }
        List<String> liste = motsByLength.computeIfAbsent(upper.length(), k -> new ArrayList<>());
        if (!liste.contains(upper)) {
            liste.add(upper);
            String msg = "Mot ajouté : " + upper + " (" + upper.length() + " lettres)";
            adminLog.info("[ADMIN] {}", msg);
            logAdmin(msg);
        } else {
            logAdmin("Ajout ignoré (déjà présent) : " + upper);
        }
        return upper;
    }

    public boolean removeWord(String mot) {
        String upper = normaliser(mot.trim());
        List<String> liste = motsByLength.getOrDefault(upper.length(), Collections.emptyList());
        boolean removed = liste.remove(upper);
        if (removed) {
            Set<String> utilises = motsUtilisesByLength.get(upper.length());
            if (utilises != null) utilises.remove(upper);
            String msg = "Mot supprimé : " + upper;
            adminLog.info("[ADMIN] {}", msg);
            logAdmin(msg);
        } else {
            String msg = "Suppression échouée (absent) : " + upper;
            adminLog.warn("[ADMIN] {}", msg);
            logAdmin(msg);
        }
        return removed;
    }

    public Map<Integer, Integer> getStatsByLength() {
        Map<Integer, Integer> stats = new TreeMap<>();
        for (int i = LONGUEUR_MIN; i <= LONGUEUR_MAX; i++) {
            stats.put(i, motsByLength.getOrDefault(i, List.of()).size());
        }
        return stats;
    }

    public List<String> getJournalAdmin() {
        return new ArrayList<>(journalAdmin);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void logAdmin(String message) {
        String entry = "[" + LocalDateTime.now().format(FMT) + "] " + message;
        journalAdmin.addFirst(entry);
        while (journalAdmin.size() > JOURNAL_MAX) {
            journalAdmin.pollLast();
        }
    }
}
