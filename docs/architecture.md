# High-Level Architecture

```mermaid
flowchart LR
  client([Client / curl])
  gw[/"API Gateway<br/>(planned - YAML scaffold only)"/]:::planned

  subgraph Identity
    auth["auth-service :8080"] --- authdb[(Postgres)]
    portal["api-portal-service :8082<br/>S2S client-credentials JWT"] --- portaldb[(Postgres)]
  end

  subgraph Catalog
    catalog["product-catalog-service :8081<br/>cached reads"] --- catdb[(Postgres)]
  end

  subgraph OrderFlow["Order flow (in-process saga)"]
    order["order-service :8085<br/>saga"] --- orderdb[(Postgres)]
    pay["payment-service :8087<br/>idempotent authorize"] --- paydb[(Postgres)]
    cart["cart-service :8083"] --- cartdb[(Postgres)]
    inv["inventory-service :8084"] --- invdb[(Postgres)]
  end

  subgraph Eligibility
    elig["eligibility-api :8086<br/>rate limiter, cache, circuit breaker"]
    eligdb[(H2 file DB)]
    redis[(Redis<br/>10m TTL cache)]
    eureka["Eureka :8761"]
    bureau[["Credit bureau<br/>(mock, force-failable)"]]
    profile[["customer-profile-service<br/>(stub, not in repo)"]]
    elig --- eligdb
  end

  client --> auth
  client --> catalog
  client --> order
  client --> elig
  client -.-> gw -.-> elig

  order -->|"HTTP: lock/clear/restore"| cart
  order -->|"HTTP: reserve/release"| inv
  order -->|"HTTP: price lookup"| catalog
  order -->|"HTTP: authorize/void<br/>circuit breaker"| pay
  portal -.->|"catalog:write token"| catalog

  elig -->|cache| redis
  elig -->|register / discover| eureka
  elig -->|"Feign / LoadBalanced"| profile
  elig -->|"@CircuitBreaker"| bureau

  notif[/"Notification Service (planned)"/]:::planned
  order -.-> notif

  classDef planned stroke-dasharray: 5 5,fill:#f6f6f6,color:#666;
```

**Legend:** solid = implemented; dashed = planned or scaffold only.

**Notes**
- Ports come from `docker-compose.yml`.
- Edges from `order-service` come from its `CART_SERVICE_URL`, `INVENTORY_SERVICE_URL`, `CATALOG_SERVICE_URL`, and `PAYMENT_SERVICE_URL` settings.
- The payment edge is authorize and void. A Resilience4j circuit breaker named `payment` sits on the order-service client. Declines do not open it. An open circuit fails authorize as `PAYMENT_UNAVAILABLE`.
- The portal-to-catalog edge is token issuance, not a runtime call: catalog validates tokens the portal signs.
- Not verified: which other services (cart, inventory) validate portal tokens.
