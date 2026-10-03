package com.ecommerce.cart.model.state;

import com.ecommerce.cart.model.CartStatus;

public class ActiveState implements CartState {

    @Override
    public void assertMutable() {
        // allowed
    }

    @Override
    public CartStatus checkout() {
        return CartStatus.CHECKED_OUT;
    }
}
