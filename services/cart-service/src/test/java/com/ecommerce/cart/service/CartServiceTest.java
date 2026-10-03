package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.exception.CartForbiddenException;
import com.ecommerce.cart.exception.CartNotActiveException;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.exception.InvalidQuantityException;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartLine;
import com.ecommerce.cart.model.CartStatus;
import com.ecommerce.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage of the cart matrix: create, add (merge and
 * validation), get (missing and foreign owner), and clear/restore keeping
 * the stored lines.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @InjectMocks
    private CartService cartService;

    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
    }

    @Test
    void create_returnsEmptyActiveCartOwnedByCaller() {
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse created = cartService.create(ownerId);

        assertThat(created.id()).isNotNull();
        assertThat(created.ownerId()).isEqualTo(ownerId);
        assertThat(created.status()).isEqualTo(CartStatus.ACTIVE);
        assertThat(created.lines()).isEmpty();
    }

    @Test
    void addItem_mergesQuantityForTheSameProductAndKeepsDistinctProducts() {
        Cart cart = ownedCart();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse once = cartService.addItem(ownerId, cart.getId(), first, 1);
        Instant beforeMerge = cart.getUpdatedAt();
        try {
            Thread.sleep(5);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        CartResponse merged = cartService.addItem(ownerId, cart.getId(), first, 2);
        assertThat(merged.updatedAt()).isAfter(beforeMerge);
        CartResponse both = cartService.addItem(ownerId, cart.getId(), second, 4);

        assertThat(once.lines()).hasSize(1);
        assertThat(merged.lines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(first);
            assertThat(line.quantity()).isEqualTo(3);
        });
        assertThat(both.lines()).hasSize(2);
        assertThat(both.status()).isEqualTo(CartStatus.ACTIVE);
    }

    @Test
    void addItem_withQuantityBelowOne_throwsValidationError() {
        Cart cart = ownedCart();

        assertThatThrownBy(() -> cartService.addItem(ownerId, cart.getId(), UUID.randomUUID(), 0))
                .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void addItem_whenCartIsNotActive_throwsCartNotActive() {
        Cart cart = ownedCart();
        cart.setStatus(CartStatus.LOCKED);
        when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.addItem(ownerId, cart.getId(), UUID.randomUUID(), 1))
                .isInstanceOf(CartNotActiveException.class);
    }

    @Test
    void get_whenMissing_throwsNotFound() {
        UUID missing = UUID.randomUUID();
        when(cartRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.get(ownerId, missing))
                .isInstanceOf(CartNotFoundException.class);
    }

    @Test
    void get_whenOwnedBySomeoneElse_throwsForbidden() {
        Cart cart = ownedCart();
        when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.get(UUID.randomUUID(), cart.getId()))
                .isInstanceOf(CartForbiddenException.class);
    }

    @Test
    void clear_hidesLinesWithoutDeletingThem_andRestoreReturnsTheSameLines() {
        Cart cart = ownedCart();
        UUID productId = UUID.randomUUID();
        cart.addLine(new CartLine(productId, 2));
        cart.setStatus(CartStatus.LOCKED);
        when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse cleared = cartService.clear(ownerId, cart.getId());

        assertThat(cleared.status()).isEqualTo(CartStatus.CHECKED_OUT);
        assertThat(cleared.lines()).isEmpty();
        assertThat(cart.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getProductId()).isEqualTo(productId);
            assertThat(line.getQuantity()).isEqualTo(2);
        });

        CartResponse restored = cartService.restore(ownerId, cart.getId());

        assertThat(restored.status()).isEqualTo(CartStatus.ACTIVE);
        assertThat(restored.lines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(productId);
            assertThat(line.quantity()).isEqualTo(2);
        });
    }

    @Test
    void lockAndUnlock_areIdempotentForTheSaga() {
        Cart cart = ownedCart();
        when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(cartService.lock(ownerId, cart.getId()).status()).isEqualTo(CartStatus.LOCKED);
        assertThatThrownBy(() -> cartService.lock(ownerId, cart.getId()))
                .isInstanceOf(CartNotActiveException.class);
        assertThat(cartService.unlock(ownerId, cart.getId()).status()).isEqualTo(CartStatus.ACTIVE);
        assertThat(cartService.unlock(ownerId, cart.getId()).status()).isEqualTo(CartStatus.ACTIVE);
        assertThat(cart.getLines()).isEmpty();
    }

    private Cart ownedCart() {
        return new Cart(ownerId);
    }
}
