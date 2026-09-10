package com.ecommerce.auth.dto;

/**
 * DTO pattern: response body for a successful login - a stateless JWT
 * access token only (v1 issues no refresh token, no session).
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    public AuthResponse(String accessToken, long expiresInSeconds) {
        this(accessToken, "Bearer", expiresInSeconds);
    }
}
