---
title: 'Cart to Order Saga — place an order from a cart with compensation'
type: 'feature'
created: '2026-09-29'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: ['{project-root}/README.md', '{project-root}/_bmad-output/implementation-artifacts/deferred-work.md']
baseline_commit: '38fbc5c6714ba8ea048eca603af09c335be4b295'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The monorepo can register users and list products, but nothing can hold a cart or place an order. Checkout needs more than one service to succeed, and a later failure must undo earlier side effects.

**Approach:** Add three services that follow the existing per-service Postgres/Flyway layout. `cart-service` owns the cart. `inventory-service` owns stock and reservations (catalog has no stock and must not be rewritten). `order-service` owns orders and an in-process saga orchestrator that calls the others over HTTP with Spring `RestClient`. There is no Kafka, Eureka, Feign, Resilience4j, or gateway in this repo; choreography and those libraries stay out. A mock payment step can be forced to decline. Compensation runs in reverse for steps that already succeeded, inside the same request.

## Boundaries & Constraints

**Always:**
- Java 21, Spring Boot 3.3.4, one Postgres database per new service, Flyway, `ddl-auto: validate`, `/api/v1/...`, `ErrorResponse(code, message, timestamp)` for ordinary errors, controllers return DTOs only.
- Customer cart and order routes require an `auth-service` access token (`Authorization: Bearer`, HS256, `sub` = user UUID), validated locally with the shared `JWT_SECRET`. A cart or order is visible only to its owner.
- Catalog is read-only here: `GET /api/v1/catalog/products/{id}` (public). Never write catalog, auth, or api-portal code.
- Each mutating service method is its own local transaction. The orchestrator is not one XA transaction.
- Reservations are all-or-nothing per order and idempotent for the same `orderId`. Stock rows use `@Version`.
- Compensations are idempotent. Only steps that completed are compensated, in reverse order.
- Happy path returns the order id and `CONFIRMED`. A compensated failure returns that order id and `CANCELLED` plus a reason code.
- `POST /orders` body field `simulatePaymentFailure` (default false) declines the mock payment.

**Never:**
- No real payment provider, no message broker, no Eureka/Feign/Resilience4j/Actuator/gateway (siblings do not use them; healthchecks wget a real route).
- No shared database. No change to auth, product-catalog, or api-portal behavior.
- No durable crash recovery. If the process dies mid-saga, a `PENDING` order and a `LOCKED` cart can remain; that recovery job is out of scope. Document it.
- Do not delete a cancelled order. Do not clear the cart before payment succeeds.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Create cart | valid user JWT | 201, empty cart `ACTIVE` owned by `sub` | N/A |
| Add item | `ACTIVE` cart, `productId`, quantity ≥ 1 | 200, line added; same product merges quantities | 400 `VALIDATION_ERROR`; 409 `CART_NOT_ACTIVE` |
| Get cart | owner | 200 cart | 404 `CART_NOT_FOUND`; 403 `CART_FORBIDDEN`; 401 `UNAUTHORIZED` |
| Place order happy | `ACTIVE` non-empty cart, catalog products exist, stock sufficient, payment approves | Lock cart, reserve, authorize, commit reservation, clear cart, order `CONFIRMED`. 201 `{orderId,status,cartId,total,lines}` | N/A |
| Unknown product | cart line not in catalog | No lock, no order row. 400 `PRODUCT_NOT_FOUND` | JSON error |
| Empty cart | no lines | 400 `CART_EMPTY` | JSON error |
| Payment declines | `simulatePaymentFailure: true`, reserve already held | Release reservation, unlock cart, order `CANCELLED`. 409 body includes `orderId`, `status`, `code=PAYMENT_DECLINED` | Compensated |
| Insufficient stock | reserve cannot fill every line | Unlock cart, order `CANCELLED`. 409 `INSUFFICIENT_STOCK` | Compensated, all-or-nothing |
| Cart owned by other user | other user's cart id | 403 `CART_FORBIDDEN` | No saga |
| Stock seed | `PUT /api/v1/inventory/stock/{productId}` `{available}` | 200 upsert. Open in v1 (same stance as portal admin) | 400 if negative |

</frozen-after-approval>

## Code Map

Reuse, do not change behavior:
- `services/auth-service/.../JwtService.java` — tokens: `sub` = user UUID, claim `email`, HS256, `app.jwt.secret` / `JWT_SECRET`. New services only verify; they do not call auth-service.
- `services/product-catalog-service/.../PortalJwtValidator.java` — pattern for local JWT checks (duplicated on purpose; no shared library).
- `services/product-catalog-service/.../ProductController.java` `GET /{id}` and `ProductResponse` (`id`, `name`, `description`, `price`, `categoryId`, `categoryName`, `createdAt`, `updatedAt`).
- `services/*/exception/ErrorResponse.java` + `GlobalExceptionHandler` — copy the record shape.
- `services/product-catalog-service/src/test/.../CatalogControllerIntegrationTest.java` — `@Testcontainers` + `PostgreSQLContainer<>("postgres:16-alpine")` + `@ServiceConnection`.
- `services/auth-service/pom.xml` — Boot 3.3.4, Java 21, jjwt 0.12.6. No root aggregator POM.
- `docker-compose.yml` — `ecommerce-net`, own Postgres, wget healthcheck, host ports 8080 auth / 8081 catalog / 8082 portal. New host ports: cart 8083, inventory 8084, order 8085.
- `eligibility-api` is not in this repo. Match auth/catalog/portal instead.

New modules (nothing here yet):
- `services/cart-service` — package `com.ecommerce.cart`, database `cartdb`.
- `services/inventory-service` — package `com.ecommerce.inventory`, database `inventorydb`.
- `services/order-service` — package `com.ecommerce.order`, database `orderdb`. Saga lives in `...order.saga`, not a fourth deployable.

## Tasks & Acceptance

**Execution:**
- [x] `services/cart-service/**` -- Cart CRUD under `/api/v1/carts`: create, add item, get, lock, unlock, clear, restore. Status `ACTIVE`/`LOCKED`/`CHECKED_OUT`. Flyway, Dockerfile, `application.yml`, user-JWT filter, unit + Testcontainers IT -- cart must be compensatable without losing lines (clear hides them; restore returns `ACTIVE` with the same lines).
- [x] `services/inventory-service/**` -- `/api/v1/inventory`: upsert/get stock; `POST /reservations` (body `orderId` + lines) hold; `POST /reservations/{id}/release`; `POST /reservations/{id}/commit`; `POST /reservations/{id}/revert`. Unique order id. `@Version` on stock. Unit + IT for hold, insufficient stock, release, commit, revert -- gives the saga a real reserve/release step catalog cannot provide.
- [x] `services/order-service/**` -- `/api/v1/orders` place + get. `OrderSagaOrchestrator` runs the steps in Design Notes via `RestClient` ports (`CartClient`, `InventoryClient`, `CatalogClient`) and `PaymentGateway` / `MockPaymentGateway`. Order rows `PENDING` → `CONFIRMED` or `CANCELLED` with `failureCode`. Unit test mocks the ports and asserts reverse compensation on payment decline and on insufficient stock. `@SpringBootTest` IT hits place-order HTTP with those ports mocked and a real order database -- proves the HTTP edge and the compensation path.
- [x] `docker-compose.yml` -- add `cart-postgres`, `inventory-postgres`, `order-postgres` and the three services on `ecommerce-net`. Order service gets `CART_SERVICE_URL`, `INVENTORY_SERVICE_URL`, `CATALOG_SERVICE_URL`, `JWT_SECRET` -- local run.
- [x] `README.md` and `services/{cart,inventory,order}-service/README.md` -- APIs, saga steps, compensation map, in-process tradeoff, how to run and `mvn test` -- required operator docs.
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- record cart, order, and this minimal inventory as built; leave payment provider, notification, and gateway deferred -- keep the backlog honest.

**Acceptance Criteria:**
- Given a user JWT, an active cart with a catalog product, and enough stock, when the user places the order, then the response is 201, the order is `CONFIRMED`, the reservation is `COMMITTED`, and the cart is `CHECKED_OUT`.
- Given the same cart with `simulatePaymentFailure: true`, when the user places the order, then the response is 409, the order is `CANCELLED` with `PAYMENT_DECLINED`, stock available is unchanged, and the cart is `ACTIVE` with its lines.
- Given stock shorter than the cart, when the user places the order, then the cart is unlocked, no units stay reserved, and the order is `CANCELLED` with `INSUFFICIENT_STOCK`.
- Given `mvn test` in each new module, when Docker is available, then the suite exits 0.

## Implementation Notes

- Cart routes: `POST /api/v1/carts`, `GET /api/v1/carts/{id}`, `POST /api/v1/carts/{id}/items`, and `POST .../lock|unlock|clear|restore`. Clear sets `CHECKED_OUT` and omits lines from the JSON; the rows stay stored. Restore returns `ACTIVE` with the same lines. Internal commands take the same user JWT.
- Inventory: open `PUT`/`GET /api/v1/inventory/stock/{productId}`. Reservations require the forwarded user JWT. Hold is all-or-nothing and returns the existing row for a repeated `orderId` (201 then 200). `@Version` is on `Stock`. Release, commit, and revert are idempotent when already in the target state.
- Order: `POST /api/v1/orders` body `{cartId, simulatePaymentFailure?}`. 201 uses `OrderResponse`. 409 after compensation uses `{orderId, status, code, message}` and is not `ErrorResponse`. `OrderSagaOrchestrator` is not one transaction; each local write and each `RestClient` call is separate. A declined payment compensates release then unlock (no void). Insufficient stock compensates unlock only.
- Assigned UUID primary keys implement `Persistable`, same reason as `auth-service` `User`, so `save()` inserts a new aggregate instead of merging.
- No Docker socket in this environment (`/var/run/docker.sock` missing). Testcontainers classes are written and were not weakened. They could not start: `CartControllerIntegrationTest`, `InventoryControllerIntegrationTest`, `OrderControllerIntegrationTest`. Unit and `@WebMvcTest` classes passed: cart 15, inventory 14, order 22.
- Crash recovery is documented in `services/order-service/README.md` and the root README. A killed process can leave `PENDING` and `LOCKED`. No recovery job.
- Follow-up: `mvn test` later passed in all three modules with Testcontainers (`postgres:16-alpine`), including the three controller integration tests. `COMMIT` removes `RESERVE` from the completed-step list so a later failure reverts and does not also release. `release` of a `REVERTED` reservation does not move stock.

## Spec Change Log

## Review Triage Log

- BH1 double checkout via idempotent `lock` — `medium` — two overlapping `place` calls can both observe `ACTIVE`, then the second `lock` no-ops and both confirm. `CartService.lock`.
- BH2 compensation aborts; client may get 500 — `medium` — `compensate` has no per-step catch, so a thrown compensation skips the rest and skips `CompensatedOrderException`. `OrderSagaOrchestrator`.
- BH3 hung/ambiguous downstream call — `medium` for missing timeouts — `ClientConfig` sets none, so `place` can sit after `lock` forever. Ambiguous "server committed, client threw" repair is the frozen no-crash-recovery limit, not a second defect.
- BH4 any JWT can release a reservation; owner can call cart commands mid-saga — `false` — cart commands already use `requireOwned`. Reservation routes require a user JWT, which is the frozen rule; owner-scoped reservations would change that rule.
- BH5 open upsert while `reserved > 0` — `low` — seed API is specified open; everyday checkout does not call it. Rejected: fix is extra state policy.
- BH6 concurrent unique-key 500 on hold/add; mismatched replay lines; optimistic lock becomes `SAGA_FAILED` — `low` for the races (everyday checkout is one request; a correct catch needs a new transaction). Mismatched replay is the specified orderId idempotency. `SAGA_FAILED` plus unlock is the failure path, not a miss.
- BH7 quantity overflow; stale `updatedAt` on merge; no remove-line — `low` overflow rejected (extra bounds). `updatedAt` not bumped on merge is a one-line miss (`medium` for a wrong cart timestamp shown on GET). No remove-line is `false` (not an API this feature defined).
- BH8 no cart/order list; no client price check — `false` — create returns the id; price is snapshotted from the catalog at place, as specified.
- BH9 `confirm`/`cancel` ignore prior status; void inserts a zero row — `false` — saga calls `confirm` only on the pending row it just inserted and `void` only after authorize persisted a row.
- BH10 order line uses catalog `id`; null price 500; `SAGA_FAILED` undocumented — use cart `productId` (`low`, direct). Null price is `false` against this repo's catalog, which rejects a missing price. `SAGA_FAILED` is the code for an unexpected step failure and should be named in the order README.
- BH11 missing FK indexes; free-form status strings; no three-process test — indexes `low` rejected. Status columns match sibling `EnumType.STRING`. The missing HTTP proof is VG1.
- BH12 `confirm` failure untested; `@Transactional` IT rollback; stale spec note — untested restore is VG2. `@Transactional` did not stop the assertions (rows are committed on the server thread). The spec note already records the later green run.
- EH1 same as BH2.
- EH2 same as BH3's ambiguous half — excluded by the frozen in-request compensation limit.
- EH3 same as BH1.
- EH4 cart lines change after the snapshot and before lock — `medium` — added units are cleared without being priced. `OrderSagaOrchestrator` after `lock`.
- EH5 lost concurrent quantity increment — `low` — same as BH6; `@Version` plus retry is more than a direct correction. Rejected.
- EH6 same as BH6 cart unique race. Rejected `low`.
- EH7 same as BH6 hold unique race. Rejected `low`.
- EH8–EH10 missing `sub` NPE in the three validators — `low` — `UUID.fromString(null)` throws `NullPointerException`, which the filter does not catch, but auth-service always sets `sub`. Rejected: extra guard, not an everyday token.
- EH11–EH14 null, negative, blank, or huge catalog price/name — `false` — `product-catalog-service` already rejects those on write; place only reads that API.
- EH15 catalog body id differs from the cart line — same as BH10.
- EH16 reserve returns a null id — `medium` — a later `release`/`commit` would NPE or hit the wrong path. Guard before `RESERVE` is recorded.
- EH17 same as BH3 timeouts.
- EH18 same as BH4. `false`.
- VG1 place never observes real cart/inventory HTTP — `patch` — mocked ports can no-op and the order IT still passes. Cover `RestCartClient`/`RestInventoryClient` with `MockRestServiceServer`.
- VG2 failure after clear never asserts restore — `patch` — `failureAfterCommit` throws from `clear` and expects `restore` not to run.
- VG3 void of an existing `AUTHORIZED` row untested — `patch` — the unit test only voids a missing row.
- VG4 other user's `GET /orders/{id}` untested on the real query service — `patch`.
- VG5 place of a non-`ACTIVE` cart untested — `patch`.
- VG6 one hold with two lines for the same product untested — `patch` — skipping `merge` could reserve more than `available`.
- VG-other compensation exception skips the 409 body — same as BH2.

## Design Notes

Orchestration, not choreography: the repo has no broker. The orchestrator in `order-service` is the only coordinator. Downstream clients are interfaces so tests do not need the other processes. Tradeoff versus Kafka choreography: compensation is immediate and easy to test, but a crash mid-request is not replayed. Document that in the order README.

Saga (forward). Stop on the first failure and compensate completed steps in reverse. Creating the `PENDING` order is local and is finished by setting `CANCELLED` rather than by a remote call.

1. Load cart (`ACTIVE`, owner, at least one line). No compensation.
2. `GET` each product from catalog; snapshot name and price. Unknown id → 400, no order row.
3. Insert order `PENDING` and lines (local transaction).
4. `LOCK_CART` — compensation `unlock` (`LOCKED` → `ACTIVE`).
5. `RESERVE` — compensation `release` (`HELD` → `RELEASED`, available restored).
6. `AUTHORIZE_PAYMENT` — mock approves unless `simulatePaymentFailure`. Compensation `void` (records a void; no external capture).
7. `COMMIT` reservation (`HELD` → `COMMITTED`, reserved count drops, available stays down) — compensation `revert` (available restored). Commit replaces the hold, so revert is not followed by release.
8. `CLEAR_CART` (`LOCKED` → `CHECKED_OUT`) — compensation `restore` (`CHECKED_OUT` → `ACTIVE`, lines still stored).
9. Mark order `CONFIRMED` (local). On failure, set `CANCELLED` after the reverse compensations.

A declined payment means `AUTHORIZE_PAYMENT` did not complete, so compensation is release, unlock, and mark `CANCELLED` (`PAYMENT_DECLINED`). `void` runs only when authorize succeeded and a later step fails. Insufficient stock fails at step 5, so only unlock and cancel run (`INSUFFICIENT_STOCK`).

Internal cart commands (lock, unlock, clear, restore) and inventory commands accept the same user JWT the orchestrator forwards. Stock upsert stays open so a local demo can seed units without a portal grant. Catalog writes are not used.

`409` placement body (not `ErrorResponse`, because the caller must see the order): `{orderId, status, code, message}`. Ordinary errors stay `ErrorResponse`.

## Verification

**Commands:**
- `mvn test` in `services/cart-service`, `services/inventory-service`, and `services/order-service` -- expected: exit 0 (Testcontainers needs Docker).

**Manual checks (if no CLI):**
- None if the commands above pass. Compose smoke is optional and not required for the suite.
