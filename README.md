# Ecommerce Microservices Monorepo

This repo hosts the services of an ecommerce platform, one directory per
service:

```
services/
  auth-service/             # User/Auth — register, login, JWT issuance, profile lookup
  product-catalog-service/  # Product Catalog — categories, products, search, CQRS-lite + caching
  api-portal-service/       # API Portal — service-to-service (S2S) client-credentials JWT issuance
  cart-service/             # Cart — per-user carts the order saga can lock, clear, and restore
  inventory-service/        # Inventory — stock and all-or-nothing reservations (optimistic locking)
  order-service/            # Order — place an order with an in-process saga
```

Each service:
- owns exactly one datastore (no shared database across services),
- is independently buildable and runnable via Docker,
- exposes a versioned REST API under `/api/v1/<service>/...`.

New services are added the same way `auth-service` was: a new
`services/<name>/` directory, its own Dockerfile, and an entry in
`docker-compose.yml`.

## Services

### auth-service

The foundational service — every other service will eventually depend on
the identities it issues tokens for. See
[`services/auth-service`](services/auth-service) for its source.

Stack: Java 21, Spring Boot 3.3.x, PostgreSQL, Flyway, JWT (HS256, access
token only — no refresh tokens, no server-side sessions).

**API** (`/api/v1/auth`):

| Method | Path        | Auth              | Purpose                       |
|--------|-------------|-------------------|--------------------------------|
| POST   | `/register` | public            | Create an account              |
| POST   | `/login`    | public            | Exchange credentials for a JWT |
| GET    | `/me`       | `Bearer <token>`  | Fetch the caller's own profile |

Design patterns made explicit in `auth-service` (see in-code comments too):

| Pattern                  | Where |
|---------------------------|-------|
| Repository                | `UserRepository` isolates persistence from `AuthService` |
| DTO                       | `dto/*` — request/response shapes never leak `User` or the password hash |
| Strategy                  | `PasswordEncoder` interface / `BCryptPasswordEncoder` implementation |
| Chain of Responsibility   | `JwtAuthFilter` in the Spring Security filter chain |
| Layered architecture      | Controller → Service → Repository, each single-responsibility |
| Centralized error handling| `GlobalExceptionHandler` (`@ControllerAdvice`) maps domain exceptions to HTTP responses in one place |

### product-catalog-service

Product listings, categories and search, built to demonstrate a
CQRS-lite, read-optimized store: a `*CommandService` owns every write and a
separate `*QueryService` owns every read, with the hottest reads (the
category list, single-product-by-id) cached in-process via Spring Cache +
Caffeine. See [`services/product-catalog-service`](services/product-catalog-service)
for its source.

Stack: Java 21, Spring Boot 3.3.x, PostgreSQL (own database, `catalogdb` —
never shared with `auth-service`), Flyway, Spring Cache + Caffeine.

**Reads are public; writes require a service-to-service JWT.** Every
`GET` endpoint is open — no auth of any kind — because categories/products
are read-heavy, largely public catalog data. `POST /categories` and
`POST /products` require a `Bearer` token minted by
[`api-portal-service`](#api-portal-service): an HS256 JWT whose `aud`
matches this service's registered name and whose `scope` includes
`catalog:write`. This is **not** an `auth-service` token — human-user auth
and service-to-service auth are deliberately separate concerns (see the
[Service-to-service (S2S) auth flow](#service-to-service-s2s-auth-flow)
section below). A missing/invalid/wrong-audience token gets `401`; a
valid token missing the `catalog:write` scope gets `403`.

**API** (`/api/v1/catalog`):

| Method | Path                              | Auth                                | Purpose                                          |
|--------|------------------------------------|--------------------------------------|---------------------------------------------------|
| POST   | `/categories`                     | `Bearer <S2S token>`, `catalog:write` | Create a category                                 |
| GET    | `/categories`                     | public                              | List every category (cached)                       |
| POST   | `/products`                       | `Bearer <S2S token>`, `catalog:write` | Create a product under a category                 |
| GET    | `/products/{id}`                  | public                              | Fetch a product by id (cached)                      |
| GET    | `/products?categoryId=&q=&page=&size=` | public                          | List/filter by category and/or text-search name+description, paged |

Design patterns made explicit in `product-catalog-service` (see in-code
comments too):

| Pattern                    | Where |
|-----------------------------|-------|
| CQRS-lite                   | `*CommandService` (writes, cache eviction) vs `*QueryService` (reads, caching) per entity |
| Repository                  | `CategoryRepository` / `ProductRepository` isolate persistence from the services |
| DTO                         | `dto/*` — request/response shapes never leak JPA entities |
| Caching (read-optimized)    | `@Cacheable` on the category list and product-by-id reads, Caffeine-backed, evicted on write |
| Layered architecture        | Controller → Service (command/query) → Repository, each single-responsibility |
| Centralized error handling  | `GlobalExceptionHandler` (`@ControllerAdvice`) maps domain exceptions to HTTP responses in one place |
| Chain of Responsibility     | `security/PortalAuthFilter` in the Spring Security filter chain, validating S2S JWTs on writes only |

### api-portal-service

Service-to-service (S2S) auth only — issues short-lived JWTs so one
internal microservice can call another's protected endpoints. It does
**not** replace `auth-service`: human users still register/login/get a
JWT from `auth-service`; `api-portal-service` exists purely so
microservices can authenticate *to each other*, which `auth-service` was
never scoped for. See [`services/api-portal-service`](services/api-portal-service)
for its source.

Stack: Java 21, Spring Boot 3.3.x, PostgreSQL (own database, `portaldb` —
never shared with any other service), Flyway, JWT (HS256, client-credentials
grant, access token only).

**Domain**: a *registered service* is any microservice known to the
portal, tagged `PRODUCER`, `CONSUMER`, or `BOTH`. A producer *declares*
which of its APIs exist and the scope(s) each requires. A *grant*
authorizes one consumer service to call one producer service for a set of
scopes — v1 auto-approves every grant on creation; there is no human
approval workflow. A consumer then exchanges its `clientId`/`clientSecret`
for a short-lived JWT scoped to exactly one producer audience.

**v1's admin API (`/services`, `/apis`, `/grants`) is intentionally
open** — no auth on those endpoints. Registering a service is itself how
a caller first obtains credentials, so there is no pre-existing identity
to authenticate the registration call with, and there is no gateway yet
to front an operator-only surface. `POST /oauth/token` is public by
nature — the request body's `clientId`/`clientSecret` *is* the
authentication, the same way `auth-service`'s `/login` authenticates via
its request body rather than a prior session. Locking the admin API down
is a natural next step once there's an operator identity to check it
against.

**API** (`/api/v1/portal`):

| Method | Path                                | Purpose                                                        |
|--------|--------------------------------------|-----------------------------------------------------------------|
| POST   | `/services`                         | Register a service (`PRODUCER`/`CONSUMER`/`BOTH`) → 201 + one-time `clientSecret` |
| GET    | `/services`                         | List every registered service (never includes the secret/hash)   |
| GET    | `/services/{id}`                    | Fetch one registered service (never includes the secret/hash)    |
| POST   | `/services/{id}/rotate-secret`      | Issue a new secret for a service → one-time `clientSecret`, old one stops working immediately |
| POST   | `/services/{id}/apis`               | Declare a producer API + its required scope(s) (service must be `PRODUCER`/`BOTH`) |
| GET    | `/services/{id}/apis`               | List a producer's declared APIs                                   |
| POST   | `/grants`                           | Grant a consumer access to a producer for a set of scopes (auto-approved) |
| GET    | `/grants?consumerServiceId=&producerServiceId=` | List grants, optionally filtered                    |
| POST   | `/oauth/token`                      | Client-credentials grant: `clientId`+`clientSecret`+`audience`(+`scope`) → JWT |

**Token shape**: HS256, signed with `app.portal.jwt.secret` /
`PORTAL_JWT_SECRET` (must match between `api-portal-service` and every
producer that validates its tokens). Claims: `iss` = `api-portal`,
`sub` = the consumer service's name, `aud` = the producer service's name,
`scope` = a single **space-delimited string** (not a JSON array — the
RFC 6749 §3.3 convention), `exp` = short-lived (15 minutes by default,
`app.portal.jwt.expiration-ms` / `PORTAL_JWT_EXPIRATION_MS`). Requesting
no `scope` at all issues every scope the grant allows; requesting specific
scopes issues the intersection of requested ∩ granted (empty intersection
→ `403`).

Design patterns made explicit in `api-portal-service` (see in-code
comments too):

| Pattern                    | Where |
|-----------------------------|-------|
| Repository                  | `RegisteredServiceRepository` / `ProducerApiRepository` / `ConsumerGrantRepository` isolate persistence from the services |
| DTO                         | `dto/*` — request/response shapes never leak JPA entities or the client secret hash |
| Strategy                    | `PasswordEncoder` interface / `BCryptPasswordEncoder` implementation, hashing client secrets exactly like `auth-service` hashes passwords |
| Layered architecture        | Controller → Service (registry/grant/token) → Repository, each single-responsibility |
| Centralized error handling  | `GlobalExceptionHandler` (`@ControllerAdvice`) maps domain exceptions (unknown audience, missing grant, scope not granted, ...) to HTTP responses in one place |

### cart-service

Shopping carts owned by the user id in an `auth-service` access token.
See [`services/cart-service`](services/cart-service). The order saga is the
only caller of lock, unlock, clear, and restore; it forwards the same user
token. Clear hides lines (`CHECKED_OUT`) without deleting them. Restore
returns `ACTIVE` with those lines.

Host port **8083**. Database `cartdb`.

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/carts` | Create an empty `ACTIVE` cart. 201. |
| GET | `/api/v1/carts/{id}` | Owner only. 404 / 403 / 401. |
| POST | `/api/v1/carts/{id}/items` | Add a product. Same product merges quantities. |
| POST | `/api/v1/carts/{id}/lock` | `ACTIVE` → `LOCKED`. |
| POST | `/api/v1/carts/{id}/unlock` | `LOCKED` → `ACTIVE`. |
| POST | `/api/v1/carts/{id}/clear` | `LOCKED` → `CHECKED_OUT` (lines kept). |
| POST | `/api/v1/carts/{id}/restore` | `CHECKED_OUT` → `ACTIVE` with the same lines. |

### inventory-service

Stock and reservations. Catalog is not given a stock column and is not
written here. See [`services/inventory-service`](services/inventory-service).
Stock rows use `@Version`. A hold is all-or-nothing per order and idempotent
for the same `orderId`.

Host port **8084**. Database `inventorydb`. `PUT` and `GET` stock are open
so a demo can seed units. Reservation commands require the user JWT.

| Method | Path | Purpose |
|--------|------|---------|
| PUT | `/api/v1/inventory/stock/{productId}` | Upsert `{available}`. 400 if negative. |
| GET | `/api/v1/inventory/stock/{productId}` | Free and reserved counts. |
| POST | `/api/v1/inventory/reservations` | Hold `{orderId, lines}`. |
| POST | `/api/v1/inventory/reservations/{id}/release` | Restore available. |
| POST | `/api/v1/inventory/reservations/{id}/commit` | Drop reserved; available stays down. |
| POST | `/api/v1/inventory/reservations/{id}/revert` | Restore available after commit. |

### order-service

Checkout. `OrderSagaOrchestrator` is the only coordinator. It calls the
other services with Spring `RestClient` (`CartClient`, `InventoryClient`,
`CatalogClient`) and a `MockPaymentGateway`. There is no broker. See
[`services/order-service`](services/order-service) for the full step list.

Host port **8085**. Database `orderdb`. Compose sets `CART_SERVICE_URL`,
`INVENTORY_SERVICE_URL`, `CATALOG_SERVICE_URL`, and `JWT_SECRET`.

`POST /api/v1/orders` with `{cartId, simulatePaymentFailure?}`.
Happy path is 201 `CONFIRMED`. A compensated failure is 409
`{orderId, status: CANCELLED, code, message}` (`PAYMENT_DECLINED` or
`INSUFFICIENT_STOCK`). Cancelled orders are not deleted. The cart is not
cleared before payment succeeds.

Saga (forward), then compensate completed steps in reverse:

1. Load an `ACTIVE` non-empty cart owned by the caller.
2. Read each product from the catalog and snapshot name and price. Unknown id → 400, no order row.
3. Insert `PENDING`.
4. Lock the cart (compensate: unlock).
5. Reserve stock (compensate: release).
6. Authorize mock payment (compensate: void — only if this step completed).
7. Commit the reservation (compensate: revert).
8. Clear the cart (compensate: restore).
9. Mark `CONFIRMED`.

A declined payment (`simulatePaymentFailure: true`) did not complete
authorize, so the compensation is release then unlock. Insufficient stock
fails at reserve, so only unlock runs.

**Tradeoff:** compensation is immediate and easy to test, and a crash
mid-request is not replayed. A `PENDING` order and a `LOCKED` cart can
remain if the process dies. That recovery job is out of scope. There is
no real payment provider.

### Service-to-service (S2S) auth flow

The end-to-end shape every future producer service will follow to protect
its writes, using `product-catalog-service` as the worked example:

1. **Register both services** with `api-portal-service` — the producer
   (`product-catalog-service`, role `PRODUCER`) and the consumer (whatever
   internal caller needs to write to the catalog, role `CONSUMER`). Each
   registration returns a `clientId` and a one-time plaintext
   `clientSecret` — store the consumer's secret now, it is never shown
   again.
2. **Declare the producer's protected API** — `POST /services/{producerId}/apis`
   with the method/path/`requiredScopes` (e.g. `catalog:write`).
3. **Grant the consumer access** — `POST /grants` naming both service ids
   and the scope(s) to grant. Auto-approved immediately.
4. **Mint a token** — the consumer calls `POST /oauth/token` with its own
   `clientId`/`clientSecret` and `audience: "product-catalog-service"`.
5. **Call the protected endpoint** — the consumer sends
   `Authorization: Bearer <token>` to `product-catalog-service`, which
   validates the signature/audience/scope *locally* (see
   `PortalJwtValidator`) — no network call back to `api-portal-service` is
   ever made per request, only the shared `PORTAL_JWT_SECRET`.

```bash
# 1. Register producer + consumer
PRODUCER=$(curl -s -X POST localhost:8082/api/v1/portal/services \
  -H 'Content-Type: application/json' \
  -d '{"name":"product-catalog-service","displayName":"Product Catalog","role":"PRODUCER"}')
PRODUCER_ID=$(echo "$PRODUCER" | jq -r .id)

CONSUMER=$(curl -s -X POST localhost:8082/api/v1/portal/services \
  -H 'Content-Type: application/json' \
  -d '{"name":"order-service","displayName":"Order Service (example caller)","role":"CONSUMER"}')
CONSUMER_ID=$(echo "$CONSUMER" | jq -r .id)
CLIENT_ID=$(echo "$CONSUMER" | jq -r .clientId)
CLIENT_SECRET=$(echo "$CONSUMER" | jq -r .clientSecret)

# 2. Declare the protected API
curl -s -X POST localhost:8082/api/v1/portal/services/$PRODUCER_ID/apis \
  -H 'Content-Type: application/json' \
  -d '{"method":"POST","pathPattern":"/api/v1/catalog/products","requiredScopes":["catalog:write"]}'

# 3. Grant the consumer access
curl -s -X POST localhost:8082/api/v1/portal/grants \
  -H 'Content-Type: application/json' \
  -d "{\"consumerServiceId\":\"$CONSUMER_ID\",\"producerServiceId\":\"$PRODUCER_ID\",\"scopes\":[\"catalog:write\"]}"

# 4. Mint a token
TOKEN=$(curl -s -X POST localhost:8082/api/v1/portal/oauth/token \
  -H 'Content-Type: application/json' \
  -d "{\"clientId\":\"$CLIENT_ID\",\"clientSecret\":\"$CLIENT_SECRET\",\"audience\":\"product-catalog-service\"}" \
  | jq -r .accessToken)

# 5. Call the protected catalog write endpoint
CATEGORY_ID=$(curl -s -X POST localhost:8081/api/v1/catalog/categories \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Electronics","description":"Gadgets and devices"}' | jq -r .id)

curl -s -X POST localhost:8081/api/v1/catalog/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Headphones\",\"description\":\"Noise-cancelling\",\"price\":149.99,\"categoryId\":\"$CATEGORY_ID\"}"
# -> 201, product body — without the Authorization header this is a 401
```

## Running locally

From the repo root:

```bash
docker compose up --build
```

This starts each service with its own Postgres on the shared `ecommerce-net`
network:

| Service | Host port |
|---------|-----------|
| `auth-service` | 8080 |
| `product-catalog-service` | 8081 |
| `api-portal-service` | 8082 |
| `cart-service` | 8083 |
| `inventory-service` | 8084 |
| `order-service` | 8085 |
Each service's Postgres data persists across restarts in its own named
volume; Flyway migrations re-apply idempotently on every boot.

Try auth-service end-to-end:

```bash
curl -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"secret123"}'
# -> 201, profile body, no password hash

curl -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"secret123"}'
# -> 200, {"accessToken": "...", "tokenType": "Bearer", ...}

curl localhost:8080/api/v1/auth/me \
  -H 'Authorization: Bearer <accessToken from above>'
# -> 200, profile matching the registered email, no password hash
```

Try product-catalog-service's public reads (no token needed):

```bash
curl "localhost:8081/api/v1/catalog/categories"
# -> 200, [] on a fresh database

curl "localhost:8081/api/v1/catalog/products?q=headphones"
# -> 200, {"content": [...], "page": 0, "size": 20, "totalElements": ..., "totalPages": ...}
```

`POST /categories` and `POST /products` now require a `Bearer` token
minted by `api-portal-service` — see the fully worked
[Service-to-service (S2S) auth flow](#service-to-service-s2s-auth-flow)
example above, which registers a producer + consumer, grants access, mints
a token, and uses it to create a category and a product end-to-end.

## Running the tests

```bash
cd services/auth-service   # or product-catalog-service, api-portal-service, cart-service, inventory-service, order-service
mvn test
```

Unit tests cover the service-layer logic; the integration test spins up a
real PostgreSQL via Testcontainers and exercises the HTTP endpoints
end-to-end, so Docker must be running locally to execute it.

## Deferred work

The following services were split out of the original "microservices
ecommerce platform" scope and deliberately **not** built here — see
[`_bmad-output/implementation-artifacts/deferred-work.md`](_bmad-output/implementation-artifacts/deferred-work.md)
for details:

- API Gateway
- Payment Service (a real provider — order-service ships a mock decline switch only)
- Notification Service

Product Catalog, API Portal, Cart, the minimal Inventory service, and Order
(in-process saga, mock payment) are built — see the service sections above.
Durable saga crash recovery is not built; see `order-service`.

**`api-portal-service` is not an API Gateway.** It issues and lets a
producer validate client-credentials JWTs; it does not route requests,
aggregate responses, rate-limit, or provide a single ingress point for
external clients. API Gateway remains deferred, above, as its own
independent piece of work.

`auth-service` also does not implement OAuth/social login or refresh-token
rotation, and does not publish events to any message broker — none exists
yet in this repo. `product-catalog-service` does not validate `auth-service`
JWTs (human-user auth stays entirely out of scope for it) and does not
publish/consume catalog-change events. `api-portal-service` issues HS256
tokens only (no RS256/JWKS), has no human approval workflow for grants, and
its admin API (service registration, API declaration, grants) is
unauthenticated in v1 — see its section above for the rationale.

## Landing assistant-built changes

When the assistant builds on a machine that cannot push to GitHub, see [`docs/landing-changes-from-assistant.md`](docs/landing-changes-from-assistant.md).
