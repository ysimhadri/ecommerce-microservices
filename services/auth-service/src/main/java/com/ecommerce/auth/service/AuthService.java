package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import com.ecommerce.auth.exception.DuplicateEmailException;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Layered architecture, service tier: owns the register/login/profile
 * business rules. Talks to persistence only through {@link UserRepository}
 * (Repository pattern) and to hashing only through {@link PasswordEncoder}
 * (Strategy pattern) - it never touches JPA or a concrete hashing algorithm
 * directly.
 */
@Service
public class AuthService {

    private static final String GENERIC_LOGIN_FAILURE = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        String email = normalize(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        User user = new User(email, passwordEncoder.encode(request.password()));
        try {
            // saveAndFlush, not save: User.id is a client-assigned UUID with no generated-key
            // dependency forcing an immediate INSERT, so Hibernate would otherwise defer the
            // actual statement to transaction-commit time - after this method has already
            // returned - letting a concurrent duplicate's constraint violation escape this
            // catch block entirely (verified empirically; plain save() does not catch it).
            User saved = userRepository.saveAndFlush(user);
            return toProfile(saved);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // A concurrent request won the race between our existsByEmail check and this
            // save() - the unique constraint on email caught it; report it the same way.
            throw new DuplicateEmailException(email);
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());

        User user = userRepository.findByEmail(email)
                // Unknown email: same generic failure as a wrong password -> no user enumeration.
                .orElseThrow(() -> new BadCredentialsException(GENERIC_LOGIN_FAILURE));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(GENERIC_LOGIN_FAILURE);
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, jwtService.expirationSeconds());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException(GENERIC_LOGIN_FAILURE));
        return toProfile(user);
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
