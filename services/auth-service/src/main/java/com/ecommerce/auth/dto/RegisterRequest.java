package com.ecommerce.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO pattern: request payload for {@code POST /api/v1/auth/register}.
 * Kept separate from {@link com.ecommerce.auth.model.User} - the caller can
 * only ever send an email + raw password, never anything persistence-shaped.
 */
public record RegisterRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be at least 8 characters")
        String password
) {
}
