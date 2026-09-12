package com.ecommerce.portal.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * Signs short-lived, stateless HS256 service-to-service (S2S) access
 * tokens for the client-credentials grant. Distinct from {@code auth-service}'s
 * JwtService: these tokens identify a calling *service*, not a human user,
 * and carry {@code iss}/{@code aud}/{@code scope} claims a producer service
 * validates before honoring a write. Secret and expiry are config-driven
 * (application.yml, overridable via env) - see {@code app.portal.jwt.*}.
 *
 * <p>The {@code scope} claim is a single space-delimited string (RFC 6749
 * §3.3 convention), not a JSON array - producer services split on
 * whitespace to get the scope set back (see product-catalog-service's
 * PortalJwtValidator, which does exactly that).
 */
@Service
public class PortalJwtService {

    private static final String ISSUER = "api-portal";

    private final SecretKey signingKey;
    private final long expirationMs;

    public PortalJwtService(
            @Value("${app.portal.jwt.secret}") String secret,
            @Value("${app.portal.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * @param consumerServiceName the calling service's slug -> {@code sub}
     * @param producerServiceName the target service's slug -> {@code aud}
     * @param scopes               the issued (requested ∩ granted) scopes -> {@code scope}
     */
    public String generateToken(String consumerServiceName, String producerServiceName, Set<String> scopes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(consumerServiceName)
                .audience().add(producerServiceName).and()
                .claim("scope", String.join(" ", scopes))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public long expirationSeconds() {
        return expirationMs / 1000;
    }
}
