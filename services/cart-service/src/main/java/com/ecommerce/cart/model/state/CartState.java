package com.ecommerce.cart.model.state;

import com.ecommerce.cart.model.CartStatus;

/**
 * GoF State pattern: behavior and transitions of a cart vary by its state.
 * Only the {@link CartStatus} enum is persisted; {@link #forStatus} rebuilds
 * the matching state object on every use.
 */
public interface CartState {

    /** @throws com.ecommerce.cart.exception.CartNotActiveException if add/update/remove/clear is not allowed. */
    void assertMutable();

    /** @return the next status, or throws CartNotActiveException if checkout is not allowed. */
    CartStatus checkout();

    static CartState forStatus(CartStatus status) {
        return switch (status) {
            case ACTIVE -> new ActiveState();
            case CHECKED_OUT -> new CheckedOutState();
        };
    }
}
