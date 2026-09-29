package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.security.CurrentUser;
import com.ecommerce.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Layered architecture, API tier: thin — validates input shape via
 * {@code @Valid} and delegates every business decision to {@link CartService}.
 * Versioned under {@code /api/v1/carts}. Every route requires the caller's
 * {@code auth-service} access token. Lock, unlock, clear, and restore are
 * the internal commands the order saga forwards that same token to.
 */
@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ResponseEntity<CartResponse> create() {
        CartResponse created = cartService.create(CurrentUser.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CartResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(cartService.get(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<CartResponse> addItem(@PathVariable UUID id, @Valid @RequestBody AddItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(CurrentUser.id(), id, request.productId(), request.quantity()));
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<CartResponse> lock(@PathVariable UUID id) {
        return ResponseEntity.ok(cartService.lock(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<CartResponse> unlock(@PathVariable UUID id) {
        return ResponseEntity.ok(cartService.unlock(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/clear")
    public ResponseEntity<CartResponse> clear(@PathVariable UUID id) {
        return ResponseEntity.ok(cartService.clear(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<CartResponse> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(cartService.restore(CurrentUser.id(), id));
    }
}
