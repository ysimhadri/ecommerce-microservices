package com.ecommerce.cart.model.state;

import com.ecommerce.cart.exception.CartNotActiveException;
import com.ecommerce.cart.model.CartStatus;

public class CheckedOutState implements CartState {

    @Override
    public void assertMutable() {
        throw new CartNotActiveException();
    }

    @Override
    public CartStatus checkout() {
        throw new CartNotActiveException();
    }
}
