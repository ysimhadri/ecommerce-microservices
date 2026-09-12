-- Flyway migration: schema is versioned here, never generated via ddl-auto.
CREATE TABLE consumer_grants (
    id                   UUID PRIMARY KEY,
    consumer_service_id  UUID NOT NULL REFERENCES registered_services (id),
    producer_service_id  UUID NOT NULL REFERENCES registered_services (id),
    scopes               VARCHAR(500) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (consumer_service_id, producer_service_id)
);
-- v1 auto-approves grants and allows exactly one grant per
-- consumer/producer pair (no human approval workflow, no versioning of
-- grants) - the UNIQUE constraint above is what enforces that; re-granting
-- the same pair is rejected (409) rather than silently overwriting scopes.

CREATE INDEX idx_consumer_grants_consumer_service_id ON consumer_grants (consumer_service_id);
CREATE INDEX idx_consumer_grants_producer_service_id ON consumer_grants (producer_service_id);
