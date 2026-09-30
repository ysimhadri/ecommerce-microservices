# inventory-service

Stock counts and per-order reservations. Catalog has no stock column and this service does not write the catalog. It owns `inventorydb`.

Stack: Java 21, Spring Boot 3.3.4, PostgreSQL, Flyway. Stock rows use JPA `@Version` (optimistic locking). Reservation commands verify the caller's `auth-service` access token locally with `JWT_SECRET`.

## API

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| PUT | `/api/v1/inventory/stock/{productId}` | open | Upsert `{available}`. 200. Negative → 400 `VALIDATION_ERROR`. Does not change `reserved`. |
| GET | `/api/v1/inventory/stock/{productId}` | open | `{productId, available, reserved}`. 404 `STOCK_NOT_FOUND` if missing. |
| POST | `/api/v1/inventory/reservations` | user JWT | Hold. Body `{orderId, lines:[{productId, quantity}]}`. All lines succeed or none change. Same `orderId` returns the existing reservation and does not hold again (201 the first time, 200 on replay). 409 `INSUFFICIENT_STOCK`. |
| POST | `/api/v1/inventory/reservations/{id}/release` | user JWT | `HELD` → `RELEASED`. Available is restored. A second call does not move stock. |
| POST | `/api/v1/inventory/reservations/{id}/commit` | user JWT | `HELD` → `COMMITTED`. Reserved drops; available stays down. |
| POST | `/api/v1/inventory/reservations/{id}/revert` | user JWT | Compensation for commit. Available is restored. A second call does not move stock. |

Stock upsert is open so a local demo can seed units without a portal grant (same stance as the portal admin API in v1). A missing stock row is treated as insufficient stock on hold.

`available` is the free count. `reserved` is units held for an order that has not committed. Commit keeps `available` down. Release and revert put units back into `available`.

A concurrent update of the same stock row fails with 409 `STOCK_CONFLICT` (`@Version`).

## Run

`docker compose up --build` from the repo root. Host port **8084**.

```bash
cd services/inventory-service
mvn test
```

`InventoryControllerIntegrationTest` needs Docker (`postgres:16-alpine`). `InventoryServiceTest` and `InventoryControllerTest` do not.
