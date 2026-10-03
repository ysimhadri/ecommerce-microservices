CREATE TABLE carts (
    id         UUID PRIMARY KEY,
    owner_id   UUID NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A caller may accumulate many CHECKED_OUT carts but never two ACTIVE ones.
CREATE UNIQUE INDEX idx_carts_owner_active_unique ON carts (owner_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_carts_owner_created ON carts (owner_id, created_at DESC);
