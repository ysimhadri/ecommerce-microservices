package com.ecommerce.catalog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Chain of Responsibility: one link in the Spring Security filter chain,
 * same role as {@code auth-service}'s {@code JwtAuthFilter} but for S2S
 * tokens minted by {@code api-portal-service} instead of user tokens minted
 * by {@code auth-service}. A valid {@code Bearer} token whose audience
 * matches this service puts the calling service's name on the
 * SecurityContext as the principal, with one {@link GrantedAuthority} per
 * scope (prefixed {@code SCOPE_}, Spring Security's own convention) - see
 * SecurityConfig for how {@code hasAuthority("SCOPE_catalog:write")} uses
 * that on the write endpoints. A missing, expired, invalid, or
 * wrong-audience token simply leaves the context unauthenticated; GET
 * requests never needed one anyway, and SecurityConfig turns a missing
 * authentication on a write endpoint into a 401.
 */
@Component
public class PortalAuthFilter extends OncePerRequestFilter {

    private final PortalJwtValidator portalJwtValidator;

    public PortalAuthFilter(PortalJwtValidator portalJwtValidator) {
        this.portalJwtValidator = portalJwtValidator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7);
            try {
                Claims claims = portalJwtValidator.parseAndValidate(token);
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    authenticate(claims, request);
                }
            } catch (JwtException | IllegalArgumentException invalidToken) {
                // Malformed / expired / bad signature / wrong audience: leave SecurityContext
                // empty. Never leak the parsing failure detail back to the client.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        Set<String> scopes = PortalJwtValidator.scopesOf(claims);
        List<GrantedAuthority> authorities = scopes.stream()
                .<GrantedAuthority>map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();

        var authToken = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
