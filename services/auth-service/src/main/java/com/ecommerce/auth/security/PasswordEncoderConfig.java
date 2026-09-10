package com.ecommerce.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Strategy pattern: {@link PasswordEncoder} is the interface every caller
 * (AuthService) depends on; {@link BCryptPasswordEncoder} is the single
 * concrete strategy wired in today. Swapping hashing algorithms later means
 * changing only this bean, never AuthService.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
