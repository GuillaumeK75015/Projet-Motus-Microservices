package com.dauphine.miage.history_stat_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Ne fait que valider les jetons émis par player-service (même secret partagé).
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Valide le jeton et renvoie ses claims en un seul parsing (signature vérifiée une fois),
     * ou {@code null} s'il est invalide/expiré. À utiliser à la place d'un couple
     * isTokenValid()+extractXxx() qui re-parserait/re-vérifierait le jeton à chaque appel.
     */
    public Claims validateAndExtractClaims(String token) {
        try {
            return extractAllClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
