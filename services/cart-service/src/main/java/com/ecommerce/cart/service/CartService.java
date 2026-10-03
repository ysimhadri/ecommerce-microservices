package com.ecommerce.cart.service;

import com.ecommerce.cart.client.CatalogClient;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.CatalogProduct;
import com.ecommerce.cart.exception.CartEmptyException;
import com.ecommerce.cart.exception.CartItemNotFoundException;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.model.CartStatus;
import com.ecommerce.cart.model.state.CartState;
import com.ecommerce.cart.repository.CartItemRepository;
import com.ecommerce.cart.repository.CartRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.UUID;

/**
 * Cart use cases. Every mutation first goes through
 * {@link CartState#forStatus}; the status is never set directly.
 *
 * <p>Mutations target the caller's most recent cart in any status, so that
 * after checkout they hit the CHECKED_OUT cart and get 409 rather than
 * silently starting a new one; only {@link #getOrCreate} starts a fresh cart.
 * {@link #getOrCreate} is deliberately not transactional: a losing
 * concurrent insert would mark an outer transaction rollback-only and
 * prevent the re-fetch.</p>
 */
@Service
public class CartService {

    private final CartRepository carts;
    private final CartItemRepository items;
    private final CatalogClient catalogClient;
    private final TransactionOperations tx;

    public CartService(CartRepository carts, CartItemRepository items, CatalogClient catalogClient,
                       TransactionOperations tx) {
        this.carts = carts;
        this.items = items;
        this.catalogClient = catalogClient;
        this.tx = tx;
    }

    /** Converges: every caller gets the same ACTIVE cart, even when losing a create race. */
    public CartResponse getOrCreate(UUID ownerId) {
        return view(findOrCreateActive(ownerId));
    }

    public CartResponse addItem(UUID ownerId, UUID productId, int quantity) {
        CatalogProduct product = catalogClient.getProduct(productId); // before any write, incl. a new cart row
        Cart cart = currentCart(ownerId);
        CartState.forStatus(cart.getStatus()).assertMutable();
        try {
            tx.executeWithoutResult(s -> doAdd(cart.getId(), product, productId, quantity));
        } catch (DataIntegrityViolationException race) {
            // concurrent add of the same product created the row first: merge into it
            tx.executeWithoutResult(s -> doAdd(cart.getId(), product, productId, quantity));
        }
        return view(cart.getId());
    }

    private void doAdd(UUID cartId, CatalogProduct product, UUID productId, int quantity) {
        Cart cart = carts.findByIdForUpdate(cartId).orElseThrow();
        CartState.forStatus(cart.getStatus()).assertMutable();
        items.findByCartIdAndProductId(cartId, productId).ifPresentOrElse(
                existing -> {
                    existing.setQuantity(existing.getQuantity() + quantity);
                    items.saveAndFlush(existing);
                },
                () -> items.saveAndFlush(new CartItem(cartId, productId, product.name(), product.price(), quantity)));
        cart.touch();
        carts.save(cart);
    }

    public CartResponse updateItem(UUID ownerId, UUID itemId, int quantity) {
        Cart cart = currentCart(ownerId);
        tx.executeWithoutResult(s -> {
            Cart c = carts.findByIdForUpdate(cart.getId()).orElseThrow();
            CartState.forStatus(c.getStatus()).assertMutable();
            CartItem item = items.findByIdAndCartId(itemId, c.getId())
                    .orElseThrow(() -> new CartItemNotFoundException(itemId));
            item.setQuantity(quantity);
            items.saveAndFlush(item);
            c.touch();
            carts.save(c);
        });
        return view(cart.getId());
    }

    public CartResponse removeItem(UUID ownerId, UUID itemId) {
        Cart cart = currentCart(ownerId);
        tx.executeWithoutResult(s -> {
            Cart c = carts.findByIdForUpdate(cart.getId()).orElseThrow();
            CartState.forStatus(c.getStatus()).assertMutable();
            CartItem item = items.findByIdAndCartId(itemId, c.getId())
                    .orElseThrow(() -> new CartItemNotFoundException(itemId));
            items.delete(item);
            c.touch();
            carts.save(c);
        });
        return view(cart.getId());
    }

    public CartResponse clear(UUID ownerId) {
        Cart cart = currentCart(ownerId);
        tx.executeWithoutResult(s -> {
            Cart c = carts.findByIdForUpdate(cart.getId()).orElseThrow();
            CartState.forStatus(c.getStatus()).assertMutable();
            items.deleteByCartId(c.getId());
            c.touch();
            carts.save(c);
        });
        return view(cart.getId());
    }

    public CartResponse checkout(UUID ownerId) {
        Cart cart = currentCart(ownerId);
        tx.executeWithoutResult(s -> {
            Cart c = carts.findByIdForUpdate(cart.getId()).orElseThrow();
            CartStatus next = CartState.forStatus(c.getStatus()).checkout();
            if (items.countByCartId(c.getId()) == 0) {
                throw new CartEmptyException();
            }
            c.setStatus(next);
            c.touch();
            carts.save(c);
        });
        return view(cart.getId());
    }

    private Cart currentCart(UUID ownerId) {
        return carts.findFirstByOwnerIdOrderByCreatedAtDesc(ownerId)
                .orElseGet(() -> findOrCreateActive(ownerId));
    }

    private Cart findOrCreateActive(UUID ownerId) {
        return carts.findByOwnerIdAndStatus(ownerId, CartStatus.ACTIVE).orElseGet(() -> {
            try {
                return carts.saveAndFlush(new Cart(ownerId));
            } catch (DataIntegrityViolationException lostRace) {
                return carts.findByOwnerIdAndStatus(ownerId, CartStatus.ACTIVE).orElseThrow(() -> lostRace);
            }
        });
    }

    private CartResponse view(Cart cart) {
        return view(cart.getId());
    }

    private CartResponse view(UUID cartId) {
        Cart cart = carts.findById(cartId).orElseThrow();
        List<CartItem> lines = items.findByCartIdOrderByProductNameAscIdAsc(cartId);
        return CartResponse.from(cart, lines);
    }
}
