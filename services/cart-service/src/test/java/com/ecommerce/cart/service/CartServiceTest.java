package com.ecommerce.cart.service;

import com.ecommerce.cart.client.CatalogClient;
import com.ecommerce.cart.dto.CatalogProduct;
import com.ecommerce.cart.exception.CartEmptyException;
import com.ecommerce.cart.exception.CartNotActiveException;
import com.ecommerce.cart.exception.InvalidProductException;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.model.CartStatus;
import com.ecommerce.cart.model.state.CartState;
import com.ecommerce.cart.repository.CartItemRepository;
import com.ecommerce.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CartServiceTest {

    CartRepository carts = mock(CartRepository.class);
    CartItemRepository items = mock(CartItemRepository.class);
    CatalogClient catalog = mock(CatalogClient.class);
    CartService service;

    UUID owner = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    Cart cart;

    @BeforeEach
    void setUp() {
        service = new CartService(carts, items, catalog, TransactionOperations.withoutTransaction());
        cart = new Cart(owner);
        when(carts.findFirstByOwnerIdOrderByCreatedAtDesc(owner)).thenReturn(Optional.of(cart));
        when(carts.findById(cart.getId())).thenReturn(Optional.of(cart));
        when(carts.findByIdForUpdate(cart.getId())).thenReturn(Optional.of(cart));
        when(items.findByCartIdOrderByProductNameAscIdAsc(any())).thenReturn(List.of());
        when(catalog.getProduct(productId)).thenReturn(new CatalogProduct(productId, "Widget", new BigDecimal("2.50")));
    }

    @Test
    void addNewProduct_createsItemWithSnapshot() {
        when(items.findByCartIdAndProductId(cart.getId(), productId)).thenReturn(Optional.empty());
        service.addItem(owner, productId, 2);
        verify(items).saveAndFlush(argThat((CartItem i) ->
                i.getQuantity() == 2 && i.getProductName().equals("Widget")
                        && i.getUnitPrice().compareTo(new BigDecimal("2.50")) == 0));
    }

    @Test
    void addExistingProduct_increasesQuantityWithoutNewRow() {
        CartItem existing = new CartItem(cart.getId(), productId, "Widget", new BigDecimal("2.50"), 1);
        when(items.findByCartIdAndProductId(cart.getId(), productId)).thenReturn(Optional.of(existing));
        service.addItem(owner, productId, 3);
        assertThat(existing.getQuantity()).isEqualTo(4);
        verify(items, times(1)).saveAndFlush(existing);
        verify(items, never()).saveAndFlush(argThat((CartItem i) -> i != existing));
    }

    @Test
    void addRace_integrityViolationOnce_thenMergesIntoExistingRow() {
        CartItem existing = new CartItem(cart.getId(), productId, "Widget", new BigDecimal("2.50"), 1);
        when(items.findByCartIdAndProductId(cart.getId(), productId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(items.saveAndFlush(argThat((CartItem i) -> i != existing)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        service.addItem(owner, productId, 3);
        assertThat(existing.getQuantity()).isEqualTo(4);
        verify(items).saveAndFlush(existing);
    }

    @Test
    void unknownProduct_rejectedBeforeAnyWrite() {
        UUID unknown = UUID.randomUUID();
        when(catalog.getProduct(unknown)).thenThrow(new InvalidProductException(unknown));
        assertThatThrownBy(() -> service.addItem(owner, unknown, 1)).isInstanceOf(InvalidProductException.class);
        verify(items, never()).saveAndFlush(any());
    }

    @Test
    void checkedOutCart_rejectsEveryMutation() {
        cart.setStatus(CartStatus.CHECKED_OUT);
        UUID itemId = UUID.randomUUID();
        assertThatThrownBy(() -> service.addItem(owner, productId, 1)).isInstanceOf(CartNotActiveException.class);
        assertThatThrownBy(() -> service.updateItem(owner, itemId, 1)).isInstanceOf(CartNotActiveException.class);
        assertThatThrownBy(() -> service.removeItem(owner, itemId)).isInstanceOf(CartNotActiveException.class);
        assertThatThrownBy(() -> service.clear(owner)).isInstanceOf(CartNotActiveException.class);
        assertThatThrownBy(() -> service.checkout(owner)).isInstanceOf(CartNotActiveException.class);
        verify(items, never()).saveAndFlush(any());
    }

    @Test
    void checkout_emptyCart_rejected() {
        when(items.countByCartId(cart.getId())).thenReturn(0L);
        assertThatThrownBy(() -> service.checkout(owner)).isInstanceOf(CartEmptyException.class);
        assertThat(cart.getStatus()).isEqualTo(CartStatus.ACTIVE);
    }

    @Test
    void checkout_withItems_movesToCheckedOut() {
        when(items.countByCartId(cart.getId())).thenReturn(1L);
        service.checkout(owner);
        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
    }

    @Test
    void stateFactory_mapsStatuses() {
        assertThat(CartState.forStatus(CartStatus.ACTIVE).checkout()).isEqualTo(CartStatus.CHECKED_OUT);
        assertThatThrownBy(() -> CartState.forStatus(CartStatus.CHECKED_OUT).assertMutable())
                .isInstanceOf(CartNotActiveException.class);
    }
}
