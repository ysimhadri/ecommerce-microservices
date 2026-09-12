package com.ecommerce.catalog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Validates HS256 service-to-service JWTs minted by {@code api-portal-service}
 * for the client-credentials grant. This is a deliberate, temporary
 * duplication of that service's signing half (same secret via
 * {@code PORTAL_JWT_SECRET}, same {@code scope} claim shape: a single
 * space-delimited string) - a tiny shared library felt like more ceremony
 * than two services warrant for one HS256 key + a handful of claims. If a
 * third service needs this, extract it into a shared module instead of
 * copying it a third time.
 *
 * <p>Unlike {@code auth-service}'s JwtService, this validator never issues
 * tokens - product-catalog-service is a producer only, never a consumer, in
 * the S2S model, so it has no reason to hold portal client credentials.
 */
@Component
public class PortalJwtValidator {

    private final SecretKey signingKey;
    private final String expectedAudience;

    public PortalJwtValidator(
            @Value("${app.portal.jwt.secret}") String secret,
            @Value("${app.portal.jwt.audience}") String expectedAudience) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expectedAudience = expectedAudience;
    }

    /**
     * Verifies the token's signature and expiry, then checks that
     * {@code aud} names this service - a token minted for some other
     * producer must not be honored here even if it is otherwise validly
     * signed by the same shared secret.
     *
     * @throws JwtException if the token is malformed, expired, the
     *                       signature does not verify, or the audience
     *                       does not match this service.
     */
    public Claims parseAndValidate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!claims.getAudience().contains(expectedAudience)) {
            throw new JwtException("Token audience does not include " + expectedAudience);
        }
        return claims;
    }

    /** The {@code scope} claim is one space-delimited string (RFC 6749 §3.3 convention) - see api-portal-service's PortalJwtService. */
    public static Set<String> scopesOf(Claims claims) {
        String raw = claims.get("scope", String.class);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Set.of(raw.trim().split("\\s+"));
    }
}
