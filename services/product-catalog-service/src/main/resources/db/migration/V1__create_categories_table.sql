-- Flyway migration: schema is versioned here, never generated via ddl-auto.
CREATE TABLE categories (
    id          UUID PRIMARY KEY,
    name        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- UNIQUE above already gives us an index for existsByNameIgnoreCase-style lookups.
