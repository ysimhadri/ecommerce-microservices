package com.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO pattern: request payload for {@code POST /api/v1/auth/login}.
 */
public record LoginRequest(

        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "password is required")
        String password
) {
}
