# cart-service

Per-user shopping carts. The order saga locks a cart, clears it after payment, and can restore the same lines if a later step fails. This service owns `cartdb` and never reads the catalog or auth databases.

Stack: Java 21, Spring Boot 3.3.4, PostgreSQL, Flyway, HS256 JWT verification (same `JWT_SECRET` as `auth-service`). Tokens are checked locally. This service does not call `auth-service`.

## API

Base path: `/api/v1/carts`. Every route requires `Authorization: Bearer <auth-service access token>`. `sub` is the user id. A cart is visible only to its owner.

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/carts` | Create an empty `ACTIVE` cart for the caller. 201. |
| GET | `/api/v1/carts/{id}` | Fetch the cart. 200, 404 `CART_NOT_FOUND`, 403 `CART_FORBIDDEN`, 401 `UNAUTHORIZED`. |
| POST | `/api/v1/carts/{id}/items` | Add `{productId, quantity}`. Quantity must be ≥ 1. The same product merges quantities. Cart must be `ACTIVE`. 200, 400 `VALIDATION_ERROR`, 409 `CART_NOT_ACTIVE`. |
| POST | `/api/v1/carts/{id}/lock` | `ACTIVE` → `LOCKED`. Idempotent if already locked. |
| POST | `/api/v1/carts/{id}/unlock` | Compensation for lock: `LOCKED` → `ACTIVE`. Idempotent if already active. |
| POST | `/api/v1/carts/{id}/clear` | `LOCKED` → `CHECKED_OUT`. Lines stay in the database and are omitted from the response. |
| POST | `/api/v1/carts/{id}/restore` | Compensation for clear: `CHECKED_OUT` → `ACTIVE` with the same lines. |

Ordinary errors use `{"code","message","timestamp"}`.

`CHECKED_OUT` hides lines in the JSON. They are not deleted, so restore returns the same product ids and quantities.

## Run

From the repo root, `docker compose up --build` starts this service on host port **8083** (container port 8080) with `cart-postgres`.

```bash
cd services/cart-service
mvn test
```

`mvn test` includes a Testcontainers suite (`CartControllerIntegrationTest`) that needs Docker and `postgres:16-alpine`. Unit tests (`CartServiceTest`, `CartControllerTest`) do not.
