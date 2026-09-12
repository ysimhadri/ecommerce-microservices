-- Flyway migration: schema is versioned here, never generated via ddl-auto.
CREATE TABLE products (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    category_id UUID NOT NULL REFERENCES categories (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Read-optimized indexes for the query side (ProductQueryService):
-- category filtering is the most common list query, and the trigram
-- indexes let a name/description ILIKE '%term%' search use a GIN index
-- instead of a sequential scan.
CREATE INDEX idx_products_category_id ON products (category_id);

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_products_name_trgm ON products USING GIN (name gin_trgm_ops);
CREATE INDEX idx_products_description_trgm ON products USING GIN (description gin_trgm_ops);
