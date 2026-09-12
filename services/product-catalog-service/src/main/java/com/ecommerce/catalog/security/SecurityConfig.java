package com.ecommerce.catalog.security;

import com.ecommerce.catalog.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Wires the Chain of Responsibility together for the write-protection cut
 * described in the README's S2S section: every {@code GET} under
 * {@code /api/v1/catalog} stays public (v1's read side is still fully
 * open), while {@code POST /categories} and {@code POST /products} require
 * a {@link PortalAuthFilter}-resolved principal holding the
 * {@code catalog:write} scope - a service-to-service JWT minted by
 * {@code api-portal-service}, never a human {@code auth-service} token.
 * Stateless, like every filter chain in this monorepo: no HTTP session is
 * ever created.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CATALOG_BASE = "/api/v1/catalog";
    private static final String WRITE_SCOPE = "SCOPE_catalog:write";

    private final PortalAuthFilter portalAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(PortalAuthFilter portalAuthFilter, ObjectMapper objectMapper) {
        this.portalAuthFilter = portalAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, CATALOG_BASE + "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, CATALOG_BASE + "/categories").hasAuthority(WRITE_SCOPE)
                        .requestMatchers(HttpMethod.POST, CATALOG_BASE + "/products").hasAuthority(WRITE_SCOPE)
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::unauthorized)
                        .accessDeniedHandler(this::forbidden))
                .addFilterBefore(portalAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** No token at all (or an invalid one) on a write endpoint -> 401, uniform JSON body, never a stack trace. */
    private void unauthorized(HttpServletRequest request,
                               HttpServletResponse response,
                               AuthenticationException authException) throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED,
                new ErrorResponse("UNAUTHORIZED", "A valid Bearer token is required for this endpoint"));
    }

    /** A valid token that is missing the required scope -> 403, distinct from "no token at all". */
    private void forbidden(HttpServletRequest request,
                            HttpServletResponse response,
                            AccessDeniedException accessDeniedException) throws IOException {
        writeError(response, HttpStatus.FORBIDDEN,
                new ErrorResponse("FORBIDDEN", "Token is missing the required scope: catalog:write"));
    }

    private void writeError(HttpServletResponse response, HttpStatus status, ErrorResponse body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
