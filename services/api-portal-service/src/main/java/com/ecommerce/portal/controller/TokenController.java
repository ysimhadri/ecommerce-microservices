package com.ecommerce.portal.controller;

import com.ecommerce.portal.dto.TokenRequest;
import com.ecommerce.portal.dto.TokenResponse;
import com.ecommerce.portal.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Layered architecture, API tier: the one endpoint every other producer
 * service in this monorepo will call. Public by nature - the
 * clientId/clientSecret in the request body *is* the authentication, the
 * same way {@code auth-service}'s {@code /login} is a public endpoint that
 * authenticates via its own request body rather than a prior session.
 */
@RestController
@RequestMapping("/api/v1/portal/oauth")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> issueToken(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(tokenService.issueToken(request));
    }
}
