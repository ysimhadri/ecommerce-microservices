-- Flyway migration: schema is versioned here, never generated via ddl-auto.
CREATE TABLE carts (
    id         UUID PRIMARY KEY,
    owner_id   UUID NOT NULL,
    status     VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_carts_owner_id ON carts (owner_id);

-- Lines are never deleted. Clear hides them by moving the cart to
-- CHECKED_OUT; restore brings the same rows back as an ACTIVE cart.
CREATE TABLE cart_lines (
    id         UUID PRIMARY KEY,
    cart_id    UUID NOT NULL REFERENCES carts (id),
    product_id UUID NOT NULL,
    quantity   INT NOT NULL CHECK (quantity >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_lines_cart_product UNIQUE (cart_id, product_id)
);
