package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateItemRequest;
import com.ecommerce.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The caller's cart is always resolved from the JWT sub claim; no cart id appears in any URL. */
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public CartResponse getOrCreate(@AuthenticationPrincipal UUID userId) {
        return cartService.getOrCreate(userId);
    }

    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal UUID userId, @Valid @RequestBody AddItemRequest req) {
        return cartService.addItem(userId, req.productId(), req.quantity());
    }

    @PatchMapping("/items/{itemId}")
    public CartResponse updateItem(@AuthenticationPrincipal UUID userId, @PathVariable UUID itemId,
                                   @Valid @RequestBody UpdateItemRequest req) {
        return cartService.updateItem(userId, itemId, req.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@AuthenticationPrincipal UUID userId, @PathVariable UUID itemId) {
        return cartService.removeItem(userId, itemId);
    }

    @DeleteMapping
    public CartResponse clear(@AuthenticationPrincipal UUID userId) {
        return cartService.clear(userId);
    }

    @PostMapping("/checkout")
    public CartResponse checkout(@AuthenticationPrincipal UUID userId) {
        return cartService.checkout(userId);
    }
}
