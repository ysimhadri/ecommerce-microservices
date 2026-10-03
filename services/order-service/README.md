# order-service

Places an order from a cart. This service owns `orderdb` and the only saga coordinator in the repo. It calls cart, inventory, catalog, and payment with Spring `RestClient`. There is no Kafka, no choreography, and no XA transaction across the databases.

Stack: Java 21, Spring Boot 3.3.4, PostgreSQL, Flyway. The caller's `auth-service` access token is verified locally (`JWT_SECRET`, `sub` = user id) and forwarded to cart, inventory, and payment. Catalog product reads are public. Payment charges are authorized in `payment-service` (USD). A Resilience4j circuit breaker named `payment` wraps that client.

## API

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/orders` | Place the caller's cart. Body `{cartId, simulatePaymentFailure?}`. `simulatePaymentFailure` defaults to false. |
| GET | `/api/v1/orders/{id}` | Fetch one order. Owner only. 404 `ORDER_NOT_FOUND`, 403 `ORDER_FORBIDDEN`, 401 `UNAUTHORIZED`. |

Happy path: **201** `{orderId, status: CONFIRMED, cartId, total, lines}`.

A failure after the order row exists: **409** `{orderId, status: CANCELLED, code, message}`. That body is not `ErrorResponse` (no `timestamp`), because the caller must see the order. The cancelled row is kept.

Ordinary errors, before any order row, use `ErrorResponse`:

| Case | Status | Code |
|------|--------|------|
| Empty cart | 400 | `CART_EMPTY` |
| Unknown catalog product | 400 | `PRODUCT_NOT_FOUND` |
| Cart owned by someone else | 403 | `CART_FORBIDDEN` |
| Cart missing | 404 | `CART_NOT_FOUND` |
| Cart not `ACTIVE` | 409 | `CART_NOT_ACTIVE` |

`lines` on the order are a price snapshot (`productId`, `productName`, `unitPrice`, `quantity`, `lineTotal`) taken from the catalog during placement.

## Saga

Forward. Stop on the first failure. Compensate completed steps in reverse. Creating the `PENDING` order is local and is finished by setting `CANCELLED`, not by a remote call.

1. Load the cart (`ACTIVE`, owned by the caller, at least one line). No compensation.
2. `GET /api/v1/catalog/products/{id}` for each line and snapshot name and price. Unknown id → 400, no order row.
3. Insert the order as `PENDING` with its lines (local transaction).
4. `LOCK_CART` — compensation `unlock` (`LOCKED` → `ACTIVE`).
5. `RESERVE` — compensation `release` (`HELD` → `RELEASED`, available restored).
6. `AUTHORIZE_PAYMENT` — `POST /api/v1/payments/authorizations` with the order id, total, currency `USD`, and `simulateDecline` copied from `simulatePaymentFailure`. Compensation is `POST /api/v1/payments/authorizations/{orderId}/void`. Nothing is captured.
7. `COMMIT` the reservation (`HELD` → `COMMITTED`, reserved count drops, available stays down) — compensation `revert` (available restored). Commit replaces the hold in the completed-step list, so a later failure reverts and does not also release (that would try to move stock a second time).
8. `CLEAR_CART` (`LOCKED` → `CHECKED_OUT`, lines kept) — compensation `restore` (`CHECKED_OUT` → `ACTIVE` with the same lines).
9. Mark the order `CONFIRMED` (local).

A declined payment (`409 PAYMENT_DECLINED`) or a payment outage (HTTP 5xx, timeout, connection failure, or an open circuit) means authorize did not complete, so compensation is **release, then unlock**, and `void` does not run. Decline cancels the order with `PAYMENT_DECLINED`. Outage cancels it with `PAYMENT_UNAVAILABLE`.

The breaker records outages and ignores declines. Window 10, minimum 5 calls, 50% failure rate, 10 seconds open. An open circuit fails the next payment call immediately as `PAYMENT_UNAVAILABLE`. The fallback does not approve the charge.

Insufficient stock fails at reserve, so compensation is **unlock only**, and the order is `CANCELLED` with `INSUFFICIENT_STOCK`. No units stay reserved. Any other step failure cancels the order with `SAGA_FAILED`.

The cart is not cleared before payment succeeds. `payment_authorizations` is not stored in `orderdb`.

### Compensation map

| Completed step | Compensation |
|----------------|--------------|
| `CLEAR_CART` | `POST /carts/{id}/restore` |
| `COMMIT` | `POST /reservations/{id}/revert` |
| `AUTHORIZE_PAYMENT` | `POST /api/v1/payments/authorizations/{orderId}/void` |
| `RESERVE` | `POST /reservations/{id}/release` |
| `LOCK_CART` | `POST /carts/{id}/unlock` |

Compensations are idempotent on the downstream services. Only steps that completed are called, and they run in reverse.

## In-process tradeoff

Compensation is immediate and the paths above are unit-tested with mocked ports. There is **no durable crash recovery**. If this process dies after the order is `PENDING` and the cart is `LOCKED`, but before the request finishes, those rows can remain. No job replays or rolls that back. A broker and that recovery job are out of scope. Payment is the simulated acquirer in `payment-service`, not an in-process mock.

Downstream base URLs:

| Env | Default outside compose |
|-----|-------------------------|
| `CART_SERVICE_URL` | `http://localhost:8083` |
| `INVENTORY_SERVICE_URL` | `http://localhost:8084` |
| `CATALOG_SERVICE_URL` | `http://localhost:8081` |
| `PAYMENT_SERVICE_URL` | `http://localhost:8087` |
| `JWT_SECRET` | same dev default as `auth-service` |

Inside compose those URLs are `http://cart-service:8080`, `http://inventory-service:8080`, `http://product-catalog-service:8080`, and `http://payment-service:8080`.

## Run

`docker compose up --build` from the repo root. Host port **8085**.

```bash
cd services/order-service
mvn test
```

`OrderControllerIntegrationTest` uses Testcontainers (`postgres:16-alpine`) for `orderdb`, mocks cart, inventory, and catalog, and stubs payment with a local HTTP server. `PaymentCircuitBreakerTest` uses the same database and a local server that returns 5xx. Both need Docker. `OrderSagaOrchestratorTest`, `OrderCommandServiceTest`, `RemoteClientsTest`, and `OrderControllerTest` do not.
