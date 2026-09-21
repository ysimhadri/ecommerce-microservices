-- Flyway migration: schema is versioned here, never generated via ddl-auto.
CREATE TABLE eligibility_decisions (
    id           UUID PRIMARY KEY,
    customer_id  VARCHAR(64) NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    status       VARCHAR(32) NOT NULL,
    reason       VARCHAR(512) NOT NULL,
    credit_score INTEGER,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_decisions_customer ON eligibility_decisions (customer_id);

CREATE TABLE customer_eligibility_history (
    id                UUID PRIMARY KEY,
    customer_id       VARCHAR(64) NOT NULL,
    product_type      VARCHAR(32) NOT NULL,
    last_status       VARCHAR(32) NOT NULL,
    last_reason       VARCHAR(512) NOT NULL,
    last_credit_score INTEGER,
    check_count       INTEGER NOT NULL DEFAULT 1,
    last_checked_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_history_customer_product UNIQUE (customer_id, product_type)
);

CREATE TABLE eligibility_audit_log (
    id           UUID PRIMARY KEY,
    customer_id  VARCHAR(64) NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    outcome      VARCHAR(32),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_customer ON eligibility_audit_log (customer_id);
