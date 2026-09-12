# Ecommerce Microservices Monorepo

This repo hosts the services of an ecommerce platform, one directory per
service:

```
services/
  auth-service/             # User/Auth — register, login, JWT issuance, profile lookup
  product-catalog-service/  # Product Catalog — categories, products, search, CQRS-lite + caching
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

**v1 has no auth**: every endpoint is open — no JWT validation against
`auth-service`. This is an intentional v1 scope cut, not an oversight:
categories/products are read-heavy, largely public catalog data, and
validating `auth-service`-issued JWTs here would mean either a shared
secret between two services that otherwise own nothing in common or a
network call per request with no gateway yet to centralize it. Public
read APIs + admin-style (unauthenticated) writes keeps v1 shippable;
wiring real JWT validation in is the natural next step once a gateway
exists to front both services.

**API** (`/api/v1/catalog`):

| Method | Path                              | Purpose                                          |
|--------|------------------------------------|---------------------------------------------------|
| POST   | `/categories`                     | Create a category                                 |
| GET    | `/categories`                     | List every category (cached)                       |
| POST   | `/products`                       | Create a product under a category                 |
| GET    | `/products/{id}`                  | Fetch a product by id (cached)                      |
| GET    | `/products?categoryId=&q=&page=&size=` | List/filter by category and/or text-search name+description, paged |

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

## Running locally

From the repo root:

```bash
docker compose up --build
```

This starts `auth-postgres` + `auth-service` and `catalog-postgres` +
`product-catalog-service`, all on the shared `ecommerce-net` network.
`auth-service` is reachable at `http://localhost:8080`,
`product-catalog-service` at `http://localhost:8081`. Each service's
Postgres data persists across restarts in its own named volume; Flyway
migrations re-apply idempotently on every boot.

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

Try product-catalog-service end-to-end:

```bash
CATEGORY_ID=$(curl -s -X POST localhost:8081/api/v1/catalog/categories \
  -H 'Content-Type: application/json' \
  -d '{"name":"Electronics","description":"Gadgets and devices"}' | jq -r .id)

curl -X POST localhost:8081/api/v1/catalog/products \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Headphones\",\"description\":\"Noise-cancelling\",\"price\":149.99,\"categoryId\":\"$CATEGORY_ID\"}"
# -> 201, product body with categoryName resolved

curl "localhost:8081/api/v1/catalog/products?categoryId=$CATEGORY_ID"
# -> 200, {"content": [...], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1}

curl "localhost:8081/api/v1/catalog/products?q=headphones"
# -> 200, same product, found by name/description search
```

## Running the tests

```bash
cd services/auth-service   # or services/product-catalog-service
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
- Cart Service
- Order Service
- Payment Service
- Inventory Service
- Notification Service

Product Catalog Service is no longer deferred — see `product-catalog-service`
above.

`auth-service` also does not implement OAuth/social login or refresh-token
rotation, and does not publish events to any message broker — none exists
yet in this repo. `product-catalog-service` does not validate `auth-service`
JWTs in v1 (see above) and does not publish/consume catalog-change events.

## Landing assistant-built changes

When the assistant builds on a machine that cannot push to GitHub, see [`docs/landing-changes-from-assistant.md`](docs/landing-changes-from-assistant.md).
