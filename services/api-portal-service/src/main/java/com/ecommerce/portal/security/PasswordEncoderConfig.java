package com.ecommerce.portal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Strategy pattern: {@link PasswordEncoder} is the interface every caller
 * (ServiceRegistryService, TokenService) depends on; {@link BCryptPasswordEncoder}
 * is the single concrete strategy wired in today, hashing client secrets
 * exactly like auth-service hashes user passwords. Swapping algorithms
 * later means changing only this bean.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
