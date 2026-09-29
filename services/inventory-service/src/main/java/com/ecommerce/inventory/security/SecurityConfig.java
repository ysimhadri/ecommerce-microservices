package com.ecommerce.inventory.security;

import com.ecommerce.inventory.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Stock upsert and read stay open so a local demo can seed units without a
 * portal grant (same stance as the portal admin API). Reservation commands
 * require the user JWT the order saga forwards.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserJwtFilter userJwtFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(UserJwtFilter userJwtFilter, ObjectMapper objectMapper) {
        this.userJwtFilter = userJwtFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.PUT, "/api/v1/inventory/stock/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory/stock/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(this::unauthorized))
                .addFilterBefore(userJwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void unauthorized(HttpServletRequest request,
                              HttpServletResponse response,
                              AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse("UNAUTHORIZED", "Authentication required or invalid token", Instant.now());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
