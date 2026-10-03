-- Flyway migration: schema is versioned here, never generated via ddl-auto.
-- One row per order. A decline or an outage does not insert a row.
CREATE TABLE payment_authorizations (
    id         UUID PRIMARY KEY,
    order_id   UUID NOT NULL UNIQUE,
    user_id    UUID NOT NULL,
    amount     NUMERIC(12, 2) NOT NULL,
    currency   VARCHAR(3) NOT NULL,
    status     VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
