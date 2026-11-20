package com.cauverystore.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.refresh.expiry-millis:604800000}")
    private long refreshTokenExpiry;

    private static final long ACCESS_TOKEN_EXPIRY = 900000L;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String email, Long userId, String username, java.util.List<String> roles, Integer tokenVersion) {
        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", roles.get(0))
                .claim("roles", roles)
                .claim("tokenVersion", tokenVersion != null ? tokenVersion : 0)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiry))
                .setId(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /** How long someone has, after a correct password, to type their authenticator code. */
    public static final long MFA_TOKEN_EXPIRY = 5 * 60 * 1000L;
    public static final String PURPOSE_MFA = "mfa";

    /**
     * A short-lived ticket handed out after a correct password when the account also needs an
     * authenticator code. The code step must present it, so the code can never stand in for the
     * password. It carries a "purpose" claim, which JwtFilter refuses, so it can never be used
     * as an access token either.
     */
    public String generateMfaToken(Long userId, Integer tokenVersion, String preferredRole) {
        return Jwts.builder()
                .claim("purpose", PURPOSE_MFA)
                .claim("userId", userId)
                .claim("tokenVersion", tokenVersion != null ? tokenVersion : 0)
                .claim("preferredRole", preferredRole)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + MFA_TOKEN_EXPIRY))
                .setId(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /** Returns the claims of a valid, unexpired MFA ticket, or null for anything else. */
    public Claims parseMfaToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Claims claims = extractAllClaims(token);
            return PURPOSE_MFA.equals(claims.get("purpose", String.class)) ? claims : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getUserIdFromToken(String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }

    public String getRoleFromToken(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public String getEmailFromToken(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String getUsernameFromToken(String token) {
        return extractAllClaims(token).get("username", String.class);
    }
}
