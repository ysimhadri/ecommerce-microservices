package com.ecommerce.portal.dto;

/**
 * DTO pattern: response body for a successful client-credentials token
 * request. {@code scope} is the space-delimited set of scopes actually
 * issued (the requested/granted intersection) - a caller that asked for
 * more than it was granted can tell from this field alone, without
 * decoding the JWT.
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String scope
) {
    public TokenResponse(String accessToken, long expiresInSeconds, String scope) {
        this(accessToken, "Bearer", expiresInSeconds, scope);
    }
}
