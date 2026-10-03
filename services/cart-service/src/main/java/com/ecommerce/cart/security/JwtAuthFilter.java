package com.ecommerce.cart.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Chain of Responsibility link: validates an auth-service-issued HS256 Bearer
 * token locally (shared secret, no network call) and places the token's
 * {@code sub} (a user UUID) on the SecurityContext as the principal. A
 * missing/invalid token leaves the context empty; SecurityConfig turns that
 * into a 401.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final SecretKey signingKey;

    public JwtAuthFilter(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            try {
                Claims claims = Jwts.parser().verifyWith(signingKey).build()
                        .parseSignedClaims(header.substring(7)).getPayload();
                String subject = claims.getSubject();
                if (subject == null || subject.isBlank()) {
                    throw new IllegalArgumentException("missing sub");
                }
                UUID userId = UUID.fromString(subject);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            } catch (JwtException | IllegalArgumentException invalid) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
