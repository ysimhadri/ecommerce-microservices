-- Flyway migration: schema is versioned here, never generated via ddl-auto.
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- UNIQUE above already gives us an index for findByEmail lookups.
