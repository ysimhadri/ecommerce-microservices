package com.ecommerce.cart.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/** Reads the user id {@link UserJwtFilter} placed on the security context. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new IllegalStateException("Authenticated user id is missing");
        }
        return userId;
    }
}
