package com.ecommerce.cart.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Verifies HS256 access tokens minted by {@code auth-service}. This is a
 * deliberate duplication of that service's verify half (same secret via
 * {@code JWT_SECRET}, {@code sub} = user UUID) — there is no shared library
 * in this repo. This service never issues tokens and never calls auth-service.
 */
@Component
public class UserJwtValidator {

    private final SecretKey signingKey;

    public UserJwtValidator(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses and verifies the token's signature and expiry, then returns the
     * {@code sub} claim as the user id.
     *
     * @throws JwtException if the token is malformed, expired, or the signature does not verify
     * @throws IllegalArgumentException if {@code sub} is not a UUID
     */
    public UUID extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }
}
