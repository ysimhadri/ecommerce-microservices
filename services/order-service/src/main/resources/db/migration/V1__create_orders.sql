-- Flyway migration: schema is versioned here, never generated via ddl-auto.
-- A cancelled order is kept. There is no crash-recovery table: a process
-- death mid-saga can leave a PENDING row (see the order-service README).
CREATE TABLE orders (
    id           UUID PRIMARY KEY,
    cart_id      UUID NOT NULL,
    owner_id     UUID NOT NULL,
    status       VARCHAR(255) NOT NULL,
    failure_code VARCHAR(64),
    total        NUMERIC(12, 2) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_owner_id ON orders (owner_id);

CREATE TABLE order_lines (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL REFERENCES orders (id),
    product_id   UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INT NOT NULL CHECK (quantity >= 1),
    line_total   NUMERIC(12, 2) NOT NULL
);

CREATE TABLE payment_authorizations (
    id         UUID PRIMARY KEY,
    order_id   UUID NOT NULL UNIQUE,
    amount     NUMERIC(12, 2) NOT NULL,
    status     VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
