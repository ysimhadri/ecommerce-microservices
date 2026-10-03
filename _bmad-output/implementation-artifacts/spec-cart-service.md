---
title: 'Cart Service — Shopping Cart State Management'
type: 'feature'
created: '2026-09-16'
status: 'done'
baseline_commit: '38fbc5c6714ba8ea048eca603af09c335be4b295'
route: 'dispatch'
review_loop_iteration: 0
context: []
---

<!-- Target: 900–1300 tokens. Above 1600 = high risk of context rot. -->

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The platform can register users (`auth-service`) and browse products (`product-catalog-service`), but there is nowhere to hold the items a shopper intends to buy before checkout — and no service yet demonstrates the State pattern for managing an entity's lifecycle.

**Approach:** Build a standalone Cart Service — get-or-create the caller's active cart, add/update/remove items, clear, and checkout — as the fourth service in the `services/` monorepo, using the same Java 21 + Spring Boot 3.3.4 + PostgreSQL + Flyway stack and layered conventions as the existing three services. Cart mutations are guarded by a real GoF State pattern (an `ActiveState`/`CheckedOutState` pair, not a bare enum check) so a checked-out cart provably rejects further edits. Every cart belongs to an authenticated `auth-service` user (Bearer token, validated locally with `auth-service`'s shared secret — a third Chain-of-Responsibility filter, mirroring `JwtAuthFilter`/`PortalAuthFilter`), and adding an item calls `product-catalog-service`'s public `GET /products/{id}` to confirm the product exists and snapshot its current name/price — this repo's first synchronous business-to-business read call.

## Boundaries & Constraints

**Always:**
- One datastore (`cartdb`) owned by this service alone.
- Every endpoint lives under `/api/v1/cart/...` and requires a valid `auth-service`-issued Bearer token; cart-service validates it locally against `auth-service`'s own `app.jwt.secret`/`JWT_SECRET` (same env var, same HS256 derivation — no network call to `auth-service`), taking the caller's `userId` from the token's `sub` claim.
- Every error response is the uniform `ErrorResponse{code, message, timestamp}` shape (matches the other three services).
- Schema is Flyway-only (`hibernate.ddl-auto: validate`).
- `Cart`/`CartItem` entities implement `Persistable<UUID>` and their repositories use `saveAndFlush(...)` on every insert-or-update-that-could-race — this repo has hit the client-assigned-UUID `merge()`-defers-INSERT bug three times already (`auth-service`, `product-catalog-service`, `api-portal-service`); it is not optional here.
- A cart holds at most one row per `(cartId, productId)` — adding an already-present product increases its quantity instead of duplicating a row.
- A caller has at most one `ACTIVE` cart at a time, enforced by a DB partial unique index on `carts(owner_id) WHERE status = 'ACTIVE'` (not a plain unique on `owner_id` — a caller may accumulate multiple `CHECKED_OUT` carts over time). Get-or-create is a *converge*, not a *race-to-fail*: every caller gets `200` with the same active cart back, including a caller who loses the create race (catch the constraint violation, re-fetch, return what the winner created — never propagate the violation as an error here, unlike every other duplicate-creation case in this repo).
- Adding an item calls `product-catalog-service`'s public `GET /api/v1/catalog/products/{id}` (no auth needed — that endpoint is public) to confirm the product exists and to snapshot its `name`/`price` at that moment; an unknown `productId` is rejected before any row is written.
- Once a cart's status is `CHECKED_OUT`, every mutating endpoint (add/update/remove/clear) rejects with `409 CART_NOT_ACTIVE` — enforced by the State pattern, not a stray `if`.
- Unit tests for state-transition and item-merge logic; an integration test exercising the real HTTP endpoints against real Postgres.

**Never:**
- Do not build Order, Payment, Inventory, Notification, or the API Gateway here — each stays deferred (see `deferred-work.md`).
- Do not add a "place order" side effect to checkout — checkout only flips the cart's own state; no Order Service exists yet to hand off to.
- Do not add a message broker/event bus.
- Do not implement cart expiry/TTL or abandoned-cart cleanup — out of scope for v1.
- Do not re-validate an item's price/existence against the catalog on every subsequent read — the snapshot taken at add-time is what the cart shows until the item is removed and re-added.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Get-or-create cart | caller has no `ACTIVE` cart at all yet | 200, new empty `ACTIVE` cart | N/A |
| Get-or-create cart | caller already has an `ACTIVE` cart | 200, that cart with items + computed total | N/A |
| Get-or-create cart | caller's only existing cart is `CHECKED_OUT` | 200, a fresh empty `ACTIVE` cart is created; the `CHECKED_OUT` cart is untouched | N/A |
| Get-or-create cart, concurrent | N simultaneous calls, caller has no `ACTIVE` cart | 200 on every call, all returning the same cart id; exactly one `carts` row ends up `ACTIVE` for that owner | N/A |
| Add item, new product | product not yet in cart | 200, item added with quantity | N/A |
| Add item, existing product | product already in cart | 200, quantity increased | N/A |
| Add item, invalid quantity | quantity ≤ 0 | 400 `VALIDATION_ERROR` | field-level message |
| Add item, unknown product | `productId` doesn't exist in the catalog | 400 `INVALID_PRODUCT_ID` | JSON error body, no row written |
| Update item quantity | known item id, new quantity > 0 | 200, quantity updated | N/A |
| Update item, unknown id | item id not in caller's cart | 404 `CART_ITEM_NOT_FOUND` | JSON error body |
| Remove item | known item id | 200, item removed | N/A |
| Clear cart | any state of items | 200, cart emptied, stays `ACTIVE` | N/A |
| Checkout | cart `ACTIVE`, has ≥1 item | 200, cart → `CHECKED_OUT` | N/A |
| Checkout, empty cart | cart `ACTIVE`, zero items | 400 `CART_EMPTY` | JSON error body |
| Mutate a checked-out cart | cart is `CHECKED_OUT` | 409 `CART_NOT_ACTIVE` | JSON error body |
| No/invalid auth on any endpoint | missing or bad token | 401 `UNAUTHORIZED` | N/A |

</frozen-after-approval>

## Code Map

**API surface** (`/api/v1/cart`) — every route resolves the caller's cart from the JWT `sub` claim; no cart id ever appears in a URL, only item ids do:

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/` | Get-or-create the caller's `ACTIVE` cart |
| POST | `/items` | Add an item (or increase quantity if the product is already present) |
| PATCH | `/items/{itemId}` | Update an item's quantity |
| DELETE | `/items/{itemId}` | Remove an item |
| DELETE | `/` | Clear the cart (empties items, cart stays `ACTIVE`) |
| POST | `/checkout` | Transition the cart to `CHECKED_OUT` |

Greenfield within the monorepo — mirrors `product-catalog-service`'s layout exactly:
- `services/cart-service/` -- new Maven/Spring Boot service root, same `pom.xml` shape as `product-catalog-service/pom.xml` (Spring Boot 3.3.4, Java 21, web/data-jpa/validation/postgresql/flyway/testcontainers) plus `security`+`jjwt-api`/`jjwt-impl`/`jjwt-jackson` (auth) and `spring-boot-starter-webclient`-equivalent (`spring-webflux`, for the blocking `WebClient` call to catalog — no reactive stack elsewhere in this service)
- `services/auth-service/src/main/java/com/ecommerce/auth/security/JwtService.java` -- reference for the token shape cart-service's filter validates: HS256, `sub` = userId (UUID string), no audience claim
- `services/auth-service/src/main/java/com/ecommerce/auth/security/JwtAuthFilter.java`, `SecurityConfig.java` -- reference implementation for cart-service's own filter (same Chain-of-Responsibility shape, no `permitAll` routes here since every cart endpoint requires auth)
- `services/product-catalog-service/src/main/java/com/ecommerce/catalog/model/Category.java` -- reference implementation of the required `Persistable<UUID>` fix (javadoc explains why)
- `services/product-catalog-service/src/main/java/com/ecommerce/catalog/exception/GlobalExceptionHandler.java` -- reference shape for cart-service's own handler, including the `MethodArgumentTypeMismatchException` handler this repo has needed in both other services
- `services/product-catalog-service/src/main/java/com/ecommerce/catalog/controller/ProductController.java` -- the target endpoint (`GET /products/{id}`) cart-service's catalog client calls; response shape is `ProductResponse{id, name, description, price, categoryId, categoryName, createdAt, updatedAt}`
- `docker-compose.yml` -- add `cart-postgres` + `cart-service` entries, same shape as the `catalog-postgres`/`product-catalog-service` pair, including the `depends_on: condition: service_healthy` and healthcheck lines that pair already has; `cart-service` maps host port `8083` (next free after `8080`/`8081`/`8082`), needs `CATALOG_SERVICE_URL` (default `http://product-catalog-service:8080`) and the shared `JWT_SECRET` auth-service already uses
- `README.md` -- add a `### cart-service` section matching the existing three

## Tasks & Acceptance

**Execution:**
- [x] `services/cart-service/pom.xml` -- Spring Boot 3.3.4 parent; web, data-jpa, validation, security, postgresql, flyway, testcontainers, jjwt-api/impl/jackson, spring-webflux (blocking `WebClient` only) -- project scaffold
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/CartServiceApplication.java` -- entry point
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/model/CartStatus.java` -- enum: `ACTIVE`, `CHECKED_OUT`
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/model/Cart.java` -- entity: id, ownerId, status (`CartStatus`, `@Enumerated(STRING)`), createdAt, updatedAt; `implements Persistable<UUID>`
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/model/CartItem.java` -- entity: id, cartId, productId, productName, unitPrice, quantity; `implements Persistable<UUID>`
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/model/state/CartState.java` -- interface: `assertMutable()` (throws `CartNotActiveException` if add/update/remove/clear isn't allowed) and `checkout()` (returns the next `CartStatus`, or throws) -- State pattern contract covering both the guard and the one real transition
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/model/state/ActiveState.java`, `CheckedOutState.java` -- the two concrete states; `CartState.forStatus(CartStatus)` static factory maps the persisted enum to its state object on every use (a JPA column can't hold a live polymorphic instance across reloads)
- [x] `services/cart-service/src/main/resources/db/migration/V1__create_carts_table.sql` -- includes `CREATE UNIQUE INDEX idx_carts_owner_active_unique ON carts (owner_id) WHERE status = 'ACTIVE'` (partial index — a caller may have multiple `CHECKED_OUT` carts over time, but never two `ACTIVE` ones)
- [x] `services/cart-service/src/main/resources/db/migration/V2__create_cart_items_table.sql` -- unique `(cart_id, product_id)`
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/repository/CartRepository.java`, `CartItemRepository.java` -- Repository pattern
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/dto/*.java` -- request/response DTOs, kept separate from entities
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/security/JwtAuthFilter.java`, `SecurityConfig.java` -- validates `auth-service` tokens locally (shared secret); Chain of Responsibility, no public routes
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/client/CatalogClient.java` -- blocking `WebClient` call to `GET {CATALOG_SERVICE_URL}/api/v1/catalog/products/{id}`; maps a 404 to `InvalidProductException`
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/service/CartService.java` -- get-or-create (find `ACTIVE` cart for owner, else `saveAndFlush` a new one; catch the partial-index violation from a losing concurrent create and re-fetch the winner's row instead of propagating an error), add/update/remove/clear (each calls `CartState.forStatus(cart.getStatus()).assertMutable()` first), checkout (calls `CartState.forStatus(...).checkout()` for the new status rather than a bare setter); calls `CatalogClient` on add
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/controller/CartController.java` -- API layer
- [x] `services/cart-service/src/main/java/com/ecommerce/cart/exception/*.java` -- domain exceptions + `GlobalExceptionHandler`
- [x] `services/cart-service/src/main/resources/application.yml` -- datasource/JPA/Flyway/JWT/catalog-URL config, env-overridable
- [x] `services/cart-service/Dockerfile` -- multi-stage build, matching the other three
- [x] `docker-compose.yml` -- `cart-postgres` + `cart-service` entries, `CATALOG_SERVICE_URL` + shared `JWT_SECRET` wired
- [x] `services/cart-service/src/test/java/.../CartServiceTest.java` -- unit tests: item-merge, quantity validation, state-guard rejection, unknown-product rejection (catalog client mocked)
- [x] `services/cart-service/src/test/java/.../CartControllerIntegrationTest.java` -- Testcontainers integration test covering the full I/O matrix; mints real `auth-service`-shaped tokens (same technique as the catalog retrofit's tests) and stubs the catalog HTTP call
- [x] `services/cart-service/src/test/java/.../ConcurrentCartCreationTest.java` -- concurrency regression test, real threads + real Postgres like the other three services' tests, but a different shape: get-or-create is a *converge*, not a *race-to-fail* — every one of N concurrent callers with no existing cart must get `200` back, all with the same cart id, and exactly one `carts` row must exist afterward (not "N-1 callers get an exception," which is what the other three services' equivalent tests prove for a true create-only operation)
- [x] `README.md` -- add the `cart-service` section

**Acceptance Criteria:**
- Given a cart with two different products, when the caller adds one of those products again, then the existing item's quantity increases and no duplicate item row is created.
- Given an `ACTIVE` cart with items, when checkout is called, then the cart's status becomes `CHECKED_OUT` and a subsequent add-item call from the same caller is rejected with `409`.
- Given N concurrent get-or-create-cart calls for a caller with no existing `ACTIVE` cart, when all N execute, then every call returns `200` with the same cart id, and exactly one `carts` row exists for that owner afterward (proven by a real-concurrency test against real Postgres, not a mock).

## Design Notes

- **State** (new to this repo) — `CartState` interface + `ActiveState`/`CheckedOutState` implementations, both the guard (`assertMutable()`, throwing `CartNotActiveException` → 409 for any add/update/remove/clear it can't allow) and the one real transition (`checkout()`, returning the next `CartStatus` or throwing). `CartService` never sets `cart.status` directly — it always goes through `CartState.forStatus(cart.getStatus())` first. This is the GoF State pattern proper — behavior *and* transitions vary by an object's internal state, expressed as swappable implementations — not an inline `if (status == CHECKED_OUT)`.
- **Chain of Responsibility** (third occurrence) — `JwtAuthFilter` validates every request's `auth-service` token before it reaches a controller, same shape as `auth-service`'s own filter and `product-catalog-service`'s `PortalAuthFilter`.
- **Repository**, **DTO**, **Layered architecture**, **Centralized error handling** — same conventions as the other three services.
- This is the repo's first synchronous service-to-service *read* call for business data (distinct from the portal's auth-token issuance): `CatalogClient` calls `product-catalog-service` directly over HTTP, with no retry/circuit-breaker (out of scope for v1 — a bare `WebClient` call that maps connection failure to a `500`).

## Verification

**Commands:**
- `cd services/cart-service && mvn test` -- expected: all unit + integration tests pass, including the concurrency test
- `docker compose up --build` -- expected: `cart-postgres` and `cart-service` containers healthy alongside the other three

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Route | Evidence |
|---|---------|---------|-------|----------|
| 1 | Signed token with no `sub` -> NPE in `JwtAuthFilter`, 500 not 401 | medium | patch | `UUID.fromString(null)` throws NPE; catch only covers `JwtException`/`IllegalArgumentException`. |
| 2 | Add with unknown product can insert a `carts` row first (violates "no row written") | medium | patch | `addItem` calls `currentCart` (may create) before `catalogClient.getProduct`. |
| 3 | Checkout/add/update race and lost-update on quantity; no row lock | medium | patch | Transaction blocks re-read cart with plain `findById`; nothing serialises them. |
| 4 | Stale `pom.xml` comments reference `PortalJwtValidator` / S2S write endpoints | low | patch | Comments at pom lines 32-35, 63-65, 130 contradict the code. |
| 5 | Test gaps: JWT wrong-key/expired/non-UUID sub, catalog 5xx, add-item retry path | medium | patch | Verified: only "garbage" token and 404 stub exist; retry catch never exercised. |
| 6 | Catch-all `Exception` handler turns 404/405/415 into 500 | low | defer | Identical pattern in all four services' `GlobalExceptionHandler`; pre-existing convention. |
| 7 | Compose healthcheck `$?` / busybox exit code 4 | low | defer | Same line as the existing three services (compose lines 43, 94, 140); pre-existing. |
| 8 | Update/remove/clear/checkout by a user with no cart create an empty cart row | low | rejected | Negligible harm; fix needs new branching. |
| 9 | Quantity overflow / no `@Max` | low | rejected | Needs an invented limit; unlikely in use. |
| 10 | `CatalogUnavailableException` -> 500 | false | rejected | Spec Design Notes say connection failure maps to 500. |
| 11 | No iss/aud check lets S2S tokens act as users | false | rejected | Portal tokens use a different secret (`PORTAL_JWT_SECRET`) so cannot validate here. |
| 12 | Hardcoded default JWT secret | false | rejected | Same dev-default convention as auth-service. |
| 13 | Add-item retry catches any integrity violation | low | rejected | Negligible; fix adds complexity. |
| 14 | Catalog null/negative price, price scale | low | rejected | Catalog contract guarantees; unlikely. |
| 15 | `getOrCreate` re-fetch could be empty | low | rejected | Requires a non-partial-index violation; unreachable here. |
| 16 | Mutations after checkout 409 until `POST /` | false | rejected | Intentional, spec-mandated 409 `CART_NOT_ACTIVE`. |
| 17 | `deleteByCartId` loads rows one by one | low | rejected | Cosmetic. |
| 18 | No GET endpoint, ops surface, service README, bare-flip checkout, Quantity-validation unit test | low | rejected | Outside spec or negligible. |
