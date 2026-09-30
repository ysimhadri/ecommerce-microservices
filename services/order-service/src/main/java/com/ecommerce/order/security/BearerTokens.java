package com.ecommerce.order.security;

import jakarta.servlet.http.HttpServletRequest;

public final class BearerTokens {

    private BearerTokens() {
    }

    public static String require(HttpServletRequest request) {
        Object value = request.getAttribute(UserJwtFilter.BEARER_TOKEN);
        if (value instanceof String token && !token.isBlank()) {
            return token;
        }
        throw new IllegalStateException("Bearer token is missing");
    }
}
