-- Flyway migration: schema is versioned here, never generated via ddl-auto.
CREATE TABLE producer_apis (
    id                   UUID PRIMARY KEY,
    producer_service_id  UUID NOT NULL REFERENCES registered_services (id),
    method               VARCHAR(10) NOT NULL,
    path_pattern         VARCHAR(500) NOT NULL,
    required_scopes      VARCHAR(500) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_producer_apis_producer_service_id ON producer_apis (producer_service_id);
