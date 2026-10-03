CREATE TABLE cart_items (
    id           UUID PRIMARY KEY,
    cart_id      UUID NOT NULL REFERENCES carts (id),
    product_id   UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
);
