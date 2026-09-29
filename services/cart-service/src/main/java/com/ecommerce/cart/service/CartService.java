package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.CartLineResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.exception.CartForbiddenException;
import com.ecommerce.cart.exception.CartInvalidStateException;
import com.ecommerce.cart.exception.CartNotActiveException;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.exception.InvalidQuantityException;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartLine;
import com.ecommerce.cart.model.CartStatus;
import com.ecommerce.cart.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Layered architecture, service tier. Each public method is its own local
 * transaction. Lock, unlock, clear, and restore are the cart steps the order
 * saga calls; clear hides lines in the response and leaves the rows in place
 * so restore can return the same {@code ACTIVE} cart.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Transactional
    public CartResponse create(UUID ownerId) {
        Cart cart = new Cart(ownerId);
        return toResponse(cartRepository.save(cart));
    }

    @Transactional(readOnly = true)
    public CartResponse get(UUID ownerId, UUID cartId) {
        return toResponse(requireOwned(ownerId, cartId));
    }

    @Transactional
    public CartResponse addItem(UUID ownerId, UUID cartId, UUID productId, int quantity) {
        if (quantity < 1) {
            throw new InvalidQuantityException("quantity: must be at least 1");
        }
        Cart cart = requireOwned(ownerId, cartId);
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new CartNotActiveException("Cart is not active");
        }
        cart.getLines().stream()
                .filter(line -> line.getProductId().equals(productId))
                .findFirst()
                .ifPresentOrElse(
                        line -> {
                            line.increaseQuantity(quantity);
                            cart.touch();
                        },
                        () -> cart.addLine(new CartLine(productId, quantity)));
        return toResponse(cartRepository.save(cart));
    }

    /** {@code ACTIVE} → {@code LOCKED}. A cart that is already locked is rejected so a second place cannot check it out. */
    @Transactional
    public CartResponse lock(UUID ownerId, UUID cartId) {
        Cart cart = requireOwned(ownerId, cartId);
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new CartNotActiveException("Cart is not active");
        }
        cart.setStatus(CartStatus.LOCKED);
        return toResponse(cartRepository.save(cart));
    }

    /** Compensation for lock: {@code LOCKED} → {@code ACTIVE}. Already active is a no-op. */
    @Transactional
    public CartResponse unlock(UUID ownerId, UUID cartId) {
        Cart cart = requireOwned(ownerId, cartId);
        if (cart.getStatus() == CartStatus.ACTIVE) {
            return toResponse(cart);
        }
        if (cart.getStatus() != CartStatus.LOCKED) {
            throw new CartInvalidStateException("Cart must be locked before it can be unlocked");
        }
        cart.setStatus(CartStatus.ACTIVE);
        return toResponse(cartRepository.save(cart));
    }

    /**
     * {@code LOCKED} → {@code CHECKED_OUT}. Lines stay stored and are omitted
     * from the response until restore. Already checked out is a no-op.
     */
    @Transactional
    public CartResponse clear(UUID ownerId, UUID cartId) {
        Cart cart = requireOwned(ownerId, cartId);
        if (cart.getStatus() == CartStatus.CHECKED_OUT) {
            return toResponse(cart);
        }
        if (cart.getStatus() != CartStatus.LOCKED) {
            throw new CartInvalidStateException("Cart must be locked before it can be cleared");
        }
        cart.setStatus(CartStatus.CHECKED_OUT);
        return toResponse(cartRepository.save(cart));
    }

    /** Compensation for clear: {@code CHECKED_OUT} → {@code ACTIVE} with the same lines. */
    @Transactional
    public CartResponse restore(UUID ownerId, UUID cartId) {
        Cart cart = requireOwned(ownerId, cartId);
        if (cart.getStatus() == CartStatus.ACTIVE) {
            return toResponse(cart);
        }
        if (cart.getStatus() != CartStatus.CHECKED_OUT) {
            throw new CartInvalidStateException("Cart must be checked out before it can be restored");
        }
        cart.setStatus(CartStatus.ACTIVE);
        return toResponse(cartRepository.save(cart));
    }

    private Cart requireOwned(UUID ownerId, UUID cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found"));
        if (!cart.getOwnerId().equals(ownerId)) {
            throw new CartForbiddenException("Cart belongs to another user");
        }
        return cart;
    }

    private CartResponse toResponse(Cart cart) {
        List<CartLineResponse> visible = cart.getStatus() == CartStatus.CHECKED_OUT
                ? List.of()
                : cart.getLines().stream()
                .map(line -> new CartLineResponse(line.getProductId(), line.getQuantity()))
                .toList();
        return new CartResponse(
                cart.getId(),
                cart.getOwnerId(),
                cart.getStatus(),
                visible,
                cart.getCreatedAt(),
                cart.getUpdatedAt());
    }
}
