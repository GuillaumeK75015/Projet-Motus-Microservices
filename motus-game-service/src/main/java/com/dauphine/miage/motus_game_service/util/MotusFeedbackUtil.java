package com.dauphine.miage.motus_game_service.util;

/**
 * Algorithme de feedback Motus à deux passes.
 * Passe 1 : lettres bien placées  → BIEN_PLACE
 * Passe 2 : lettres présentes mais mal placées → MAL_PLACE, sinon → ABSENT
 *
 * Classe utilitaire statique pour pouvoir être testée indépendamment
 * de tout contexte Spring / base de données.
 */
public final class MotusFeedbackUtil {

    private MotusFeedbackUtil() {}

    public static String[] compute(String guess, String secret) {
        int len = secret.length();
        String[] feedback   = new String[len];
        boolean[] secretUsed  = new boolean[len];
        boolean[] guessMatched = new boolean[guess.length()];

        // Passe 1 — correspondances exactes
        for (int i = 0; i < len; i++) {
            if (guess.charAt(i) == secret.charAt(i)) {
                feedback[i]     = "BIEN_PLACE";
                secretUsed[i]   = true;
                guessMatched[i] = true;
            }
        }

        // Passe 2 — lettres présentes à mauvaise position
        for (int i = 0; i < guess.length(); i++) {
            if (!guessMatched[i]) {
                boolean found = false;
                for (int j = 0; j < len; j++) {
                    if (!secretUsed[j] && guess.charAt(i) == secret.charAt(j)) {
                        feedback[i]  = "MAL_PLACE";
                        secretUsed[j] = true;
                        found = true;
                        break;
                    }
                }
                if (!found) feedback[i] = "ABSENT";
            }
        }

        return feedback;
    }
}
