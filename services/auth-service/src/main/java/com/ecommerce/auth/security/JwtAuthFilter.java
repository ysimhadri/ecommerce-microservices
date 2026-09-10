package com.ecommerce.auth.security;

import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Chain of Responsibility: one link in the Spring Security filter chain.
 * Each request passes through here first; if a valid {@code Bearer} token
 * is present, the resolved {@link User} is placed on the SecurityContext
 * and the request is handed to the next filter in the chain. A missing,
 * expired, or invalid token simply leaves the context unauthenticated - the
 * chain's downstream authorization rules (see SecurityConfig) are what
 * ultimately turn that into a 401 for protected routes.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
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
                UUID userId = jwtService.extractUserId(token);
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    userRepository.findById(userId).ifPresent(user -> authenticate(user, request));
                }
            } catch (JwtException | IllegalArgumentException invalidToken) {
                // Malformed / expired / bad signature: leave SecurityContext empty.
                // Never leak the parsing failure detail back to the client.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(User user, HttpServletRequest request) {
        var authToken = new UsernamePasswordAuthenticationToken(user, null, List.of());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
