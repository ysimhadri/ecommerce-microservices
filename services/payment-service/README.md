# payment-service

Idempotent charges for the order saga. This service owns `paymentdb` and a simulated acquirer. There is no Stripe account, no capture API, and no broker. `order-service` calls authorize and void over HTTP and keeps the circuit breaker on its own client.

Stack: Java 21, Spring Boot 3.3.4, PostgreSQL, Flyway. Every route requires the caller's `auth-service` access token, checked locally (`JWT_SECRET`, HS256, `sub` = user id).

## API

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/payments/authorizations` | Authorize `{orderId, amount, currency, simulateDecline?, simulateOutage?}`. |
| POST | `/api/v1/payments/authorizations/{orderId}/void` | Void that order's charge. |

Authorize success is `{id, orderId, amount, currency, status}`. A new charge is **201** `AUTHORIZED`. The same user, order, amount, and currency is **200** with the same `id` and does not insert again. Currency is stored as three uppercase letters, so `usd` matches `USD`.

| Case | Status | Code |
|------|--------|------|
| Different amount or currency for an existing `orderId` | 409 | `PAYMENT_CONFLICT` |
| `simulateDecline: true` and no row | 409 | `PAYMENT_DECLINED` |
| `simulateOutage: true` | 503 | `PAYMENT_UNAVAILABLE` |
| JWT `sub` is not the owner | 403 | `PAYMENT_FORBIDDEN` |
| Missing or invalid bearer | 401 | `UNAUTHORIZED` |

A stored `AUTHORIZED` or `VOIDED` row wins over a later `simulateDecline`: the call replays the row and does not decline. Decline and outage insert nothing. Outage does not rewrite a row that is already there.

Void moves `AUTHORIZED` to `VOIDED`. A second void stays `VOIDED`. A missing row is **200** with an empty body and inserts nothing. Another user's void is 403 and does not change the row.

Errors use `ErrorResponse` `{code, message, timestamp}`.

## Run

`docker compose up --build` from the repo root. Host port **8087**. Postgres is not published on a host port.

```bash
cd services/payment-service
mvn test
```

`PaymentControllerIntegrationTest` uses Testcontainers (`postgres:16-alpine`). It needs Docker. The suite covers a new charge, an idempotent retry, and an outage that stores no row.
