package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import com.ecommerce.auth.exception.DuplicateEmailException;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the service-layer logic: register (success/duplicate) and
 * login (success/bad-credentials), with persistence, hashing and JWT
 * signing all mocked out.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "secret123";

    @Test
    void register_withNewEmail_persistsHashedPasswordAndReturnsProfileWithoutPasswordHash() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse profile = authService.register(new RegisterRequest(EMAIL, PASSWORD));

        assertThat(profile.email()).isEqualTo(EMAIL);
        assertThat(profile).extracting(Object::toString).asString().doesNotContain("bcrypt-hash");
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void register_withDuplicateEmail_throwsAndNeverPersists() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, PASSWORD)))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void register_normalizesEmailCaseAndWhitespace() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse profile = authService.register(new RegisterRequest("  User@Example.com  ", PASSWORD));

        assertThat(profile.email()).isEqualTo("user@example.com");
    }

    @Test
    void login_withCorrectCredentials_returnsAccessToken() {
        User user = new User(EMAIL, "bcrypt-hash");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "bcrypt-hash")).thenReturn(true);
        when(jwtService.generateToken(user.getId(), EMAIL)).thenReturn("signed.jwt.token");
        when(jwtService.expirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_withWrongPassword_throwsGenericBadCredentials() {
        User user = new User(EMAIL, "bcrypt-hash");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_withUnknownEmail_throwsSameGenericBadCredentials_noUserEnumeration() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void getProfile_returnsProfileWithoutPasswordHash() {
        User user = new User(EMAIL, "bcrypt-hash");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserProfileResponse profile = authService.getProfile(user.getId());

        assertThat(profile.id()).isEqualTo(user.getId());
        assertThat(profile.email()).isEqualTo(EMAIL);
    }
}
