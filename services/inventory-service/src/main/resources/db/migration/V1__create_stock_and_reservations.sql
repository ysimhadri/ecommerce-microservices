-- Flyway migration: schema is versioned here, never generated via ddl-auto.
-- Stock is not part of the catalog database. version is the optimistic-lock column.
CREATE TABLE stock (
    product_id UUID PRIMARY KEY,
    available  INT NOT NULL CHECK (available >= 0),
    reserved   INT NOT NULL CHECK (reserved >= 0),
    version    BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservations (
    id         UUID PRIMARY KEY,
    order_id   UUID NOT NULL UNIQUE,
    status     VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservation_lines (
    id             UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations (id),
    product_id     UUID NOT NULL,
    quantity       INT NOT NULL CHECK (quantity >= 1),
    CONSTRAINT uq_reservation_lines_reservation_product UNIQUE (reservation_id, product_id)
);
