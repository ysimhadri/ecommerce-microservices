-- Flyway migration: schema is versioned here, never generated via ddl-auto.
CREATE TABLE registered_services (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(100) NOT NULL UNIQUE,
    display_name        VARCHAR(150) NOT NULL,
    base_url            VARCHAR(500),
    role                VARCHAR(20) NOT NULL,
    client_id           VARCHAR(150) NOT NULL UNIQUE,
    client_secret_hash  VARCHAR(255) NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- UNIQUE above already gives us indexes for the by-name (audience/consumer
-- lookup) and by-client-id (token endpoint authentication) lookups.
