---
title: 'Payment Service — idempotent charges the order saga calls over HTTP'
type: 'feature'
created: '2026-10-03'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/deferred-work.md', '{project-root}/docs/architecture.md']
baseline_commit: '1ad8b3936bf778e829ad840ba3542b9c4f1b60f1'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Checkout still authorizes through `order-service`'s in-process `MockPaymentGateway`. There is no payment ledger, and a provider outage cannot fail the saga through a circuit breaker.

**Approach:** Add `payment-service` (own Postgres, simulated acquirer, no Stripe). The saga keeps its step order and calls it over HTTP. Authorize is idempotent on `orderId`. A Resilience4j circuit breaker on the order-service client turns an outage into a failed authorize so existing compensation runs.

## Boundaries & Constraints

**Always:**
- Java 21, Spring Boot 3.3.4, package `com.ecommerce.payment`, own POM, Flyway, `ddl-auto: validate`, `/api/v1/...`, `ErrorResponse(code, message, timestamp)`, controllers return DTOs only.
- Routes require the auth-service user JWT the saga already forwards (HS256, `sub` = user UUID), checked locally with `JWT_SECRET`.
- One row per `orderId`. Same user, amount, and currency replays it (201 then 200) and does not insert again. A different amount or currency is 409 `PAYMENT_CONFLICT`. A stored `AUTHORIZED` or `VOIDED` row wins over a later `simulateDecline`.
- `simulateDecline: true` with no row: 409 `PAYMENT_DECLINED`, nothing stored. `simulateOutage: true`: 503 `PAYMENT_UNAVAILABLE`, nothing stored.
- Saga order stays lock, reserve, authorize, commit, clear, confirm. Decline and outage do not complete authorize, so compensation is release then unlock and does not void. Decline code stays `PAYMENT_DECLINED`. Outage (HTTP 5xx, timeout, connection failure, or open circuit) uses `PAYMENT_UNAVAILABLE`.
- Void runs only after authorize completed and a later step failed. `AUTHORIZED` becomes `VOIDED`; a second void stays `VOIDED`; a missing row is a 200 no-op and inserts nothing.
- Breaker is on the order-service payment client. Declines are ignored. Outages are recorded. An open circuit fails immediately as `PAYMENT_UNAVAILABLE`. `POST /orders` keeps `simulatePaymentFailure` and forwards it as `simulateDecline`. No new place-order field.

**Never:**
- No processor credentials, broker, Eureka, Feign, Redis, or gateway. Do not redo cart, inventory, catalog, auth, api-portal, or eligibility-api. Do not change saga step order or add crash recovery.
- Do not leave `MockPaymentGateway` as the payment bean, and do not keep `payment_authorizations` in `orderdb`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Authorize | valid JWT, new `orderId`, amount > 0 | 201 `{id, orderId, amount, currency, status: AUTHORIZED}` | N/A |
| Idempotent retry | same user, `orderId`, amount, currency | 200, same `id`, one row | N/A |
| Amount mismatch | same `orderId`, different amount | 409 `PAYMENT_CONFLICT`, row unchanged | JSON error |
| Decline | `simulateDecline: true`, no row | 409 `PAYMENT_DECLINED`, no row | Saga `PAYMENT_DECLINED`: release, unlock, no void |
| Outage | `simulateOutage`, HTTP 5xx, unreachable, or circuit open | payment 503 `PAYMENT_UNAVAILABLE`; saga 409 `CANCELLED` / `PAYMENT_UNAVAILABLE` | Release, unlock, no void, no charge row |
| Void | authorize completed, later step fails | row `VOIDED`; second void stays `VOIDED` | If void throws, compensation continues |
| Other user | JWT `sub` is not the owner | 403 `PAYMENT_FORBIDDEN` | No rewrite |
| No token | missing bearer | 401 `UNAUTHORIZED` | JSON error |

</frozen-after-approval>

## Code Map

Do not change cart, inventory, catalog, auth, or eligibility behavior.
- `services/order-service/.../saga/OrderSagaOrchestrator.java` — keep `Step` order and reverse `compensate`. Pass `bearerToken` into payment. Map `PaymentUnavailableException` in `failureCode` / `failureMessage`.
- `services/order-service/.../payment/PaymentGateway.java` — add the bearer argument to `authorize` and `voidAuthorization`. Delete `MockPaymentGateway`.
- `services/order-service/.../client/ClientConfig.java`, `RestInventoryClient.java`, `RemoteClientsTest.java` — copy the 3s `RestClient`, `onStatus` mapping, and `MockRestServiceServer.bindTo` test. New property `app.clients.payment-base-url` / `PAYMENT_SERVICE_URL`.
- `services/order-service/.../exception/OrderExceptions.java` — add `PaymentUnavailableException`. `GlobalExceptionHandler` already maps `CompensatedOrderException` to 409 `{orderId, status, code, message}`.
- `services/order-service/src/test/.../OrderSagaOrchestratorTest.java` — copy `paymentDecline_releasesThenUnlocks_andDoesNotVoid` for outage (no void).
- `services/order-service/src/test/.../OrderControllerIntegrationTest.java` — stop asserting `PaymentAuthorizationRepository`. Stub payment with a local HTTP server and `PAYMENT_SERVICE_URL`. Leave cart, inventory, and catalog as `@MockBean`.
- `services/eligibility-api/.../client/CreditBureauClient.java` plus `resilience4j.circuitbreaker` in its `application.yml` — copy `@CircuitBreaker`, fallback, and record/ignore only. Import Spring Cloud BOM `2023.0.3` in order-service so `resilience4j-spring-boot3` resolves, and add `spring-boot-starter-aop`. Breaker name `payment`: window 10, minimum 5, 50% threshold, 10s open. Tests shrink the window. No Eureka, Feign, Redis, or rate limiter.
- `services/order-service` `pom.xml`, `Dockerfile`, `security/SecurityConfig.java`, `exception/ErrorResponse.java` — copy into `payment-service`. UUID ids implement `Persistable`.
- `db/migration/V1__create_orders.sql` — add `V2__drop_payment_authorizations.sql`. Delete order-service `PaymentAuthorization`, `PaymentStatus`, and `PaymentAuthorizationRepository`.
- `docker-compose.yml` — ports 8080–8086, 6379, and 8761 are taken. Publish payment on host **8087**. No host port for its Postgres. wget healthcheck; only exit 4 is unhealthy.
- `docs/architecture.md`, `README.md`, `services/order-service/README.md` — payment is a real HTTP dependency. `deferred-work.md` — mark only this payment item done.

## Tasks & Acceptance

**Execution:**
- [x] `services/payment-service/**` -- Boot module with authorize, replay, conflict, decline, outage, void, user-JWT filter, Flyway, Dockerfile, `application.yml`, and README. Test the charge rules, including success, replay, and outage with no row -- ledger the saga can call.
- [x] `services/order-service/**` -- `RestPaymentClient` (`@CircuitBreaker`) behind `PaymentGateway`. Forward `simulatePaymentFailure` as `simulateDecline`. Drop the local payment table. Tests: decline still compensates without void; outage compensates without void; repeated 5xx opens the breaker and the next call is `PAYMENT_UNAVAILABLE` -- transport changes, step order does not.
- [x] `docker-compose.yml` -- `payment-postgres` and `payment-service` on 8087; order-service gets `PAYMENT_SERVICE_URL=http://payment-service:8080` and depends on payment health -- compose can place an order.
- [x] `README.md`, `services/order-service/README.md`, `docs/architecture.md`, `_bmad-output/implementation-artifacts/deferred-work.md` -- document the HTTP step, breaker, and port 8087; mark this deferred item done only -- backlog matches what shipped.

**Acceptance Criteria:**
- Given a user JWT and a new order id, when authorize is retried with the same amount, then both calls return the same payment id and one `AUTHORIZED` row exists.
- Given `simulatePaymentFailure: true`, when the saga runs, then the order is `CANCELLED` / `PAYMENT_DECLINED`, stock is released, the cart is unlocked, and payment stored nothing.
- Given payment returns 503 or the circuit is open, when the user places an order, then the order is `CANCELLED` / `PAYMENT_UNAVAILABLE`, release and unlock run, and void does not.
- Given `mvn test` in `services/payment-service` and `services/order-service`, when Docker is available, then both suites exit 0.

## Implementation Notes

- `payment-service` ledger is idempotent on `orderId`. Currency is normalized to three uppercase letters. A unique-constraint race replays the stored row in a new transaction.
- Order charges are sent as `USD`. `simulatePaymentFailure` is forwarded as `simulateDecline`. The breaker name is `payment`.
- `mvn test` in `services/payment-service`: 15 tests, 0 failures. `mvn test` in `services/order-service`: 39 tests, 0 failures, including decline compensation, 503 compensation, an open circuit that does not call payment, a void after commit failure, a read timeout through the payment client bean, and a void that throws while unlock and cancel still run.
- The payment image installs GNU wget so the compose healthcheck's exit 4 means the port is closed. BusyBox wget exits 1 for that case and for HTTP 401.

## Spec Change Log

## Review Triage Log

- BH1 shared `payment` breaker fails void — `false` — a failed void is the matrix row "if void throws, compensation continues". Authorize outages never reach void because that step did not complete.
- BH2 timeout after a committed charge leaves `AUTHORIZED` — `false` — the frozen rules treat timeout as an outage that does not void, and crash recovery is out of scope.
- BH3 a 200 `VOIDED` replay is treated as a new authorize — `false` — `CustomerOrder` assigns a new id before authorize, so place-order cannot replay a voided charge.
- BH4 other 4xx become `SAGA_FAILED` and do not open the breaker — `false` — only declines and outages have their own codes; `recordExceptions` lists outages, not client errors.
- BH5 decline is logged as a fallback — `low` — rejected. The saga log already names `PAYMENT_DECLINED` versus `PAYMENT_UNAVAILABLE`. Filtering that warn adds a branch.
- BH6 half-up rounding and no digit cap — `low` — rejected. Scale 2 is the stored charge. A 10-digit guard is extra, and catalog totals fit `NUMERIC(12, 2)`.
- BH7 every integrity error is treated as an insert race — `low` — rejected for the overflow path (same guard as BH6). The missing concurrent test is the deferred entry below.
- BH8 a zero catalog price becomes `SAGA_FAILED` — `low` — rejected. Catalog allows `price >= 0`, and payment correctly rejects a non-positive amount. A free-checkout path is new behavior.
- BH9 BusyBox wget makes the healthcheck pass when nothing is listening — `medium` — `wget` in `eclipse-temurin:21-jre-alpine` exits 1 for both connection refusal and HTTP 401, so `test $? -ne 4` is success either way. Patch: install GNU wget.
- BH10 dropping `orderdb.payment_authorizations` loses old rows — `false` — the spec requires that drop. Those rows were the in-process mock, not a provider ledger.
- BH11 authorize can answer from a stale `AUTHORIZED` entity after a concurrent void — `low` — rejected. One place-order uses one new id and does not void and authorize at the same time. A version column is new state.
- BH12 root README says every replay is `AUTHORIZED` — `low` — a stored `VOIDED` row is replayed as `VOIDED`. Patch the sentence.
- BH13 place-order and payment tests miss amount, bearer, void, invalid token, and timeout — `medium` — same gaps as the verification-gap rows below.
- EC1 amount above `NUMERIC(12, 2)` returns 500 — `low` — rejected. Same as BH6.
- EC2 a JWT with no `sub` becomes 500 — `low` — rejected. auth-service always sets `sub`. A null check is an extra guard.
- EC3 read timeout leaves an authorized charge — `false` — same as BH2.
- EC4 an open circuit skips void — `false` — same as BH1.
- VG1 place-order does not pin the charge amount or bearer — `medium` — the happy-path stub ignores both, so a wrong amount or a dropped token still returns 201. Patch the assertions.
- VG2 void after a later step failure never hits the payment stub — `medium` — saga tests mock `PaymentGateway`. A no-op HTTP void would still pass. Patch a place-order test that counts one void.
- VG3 a non-empty invalid bearer is never rejected — `medium` — only a missing header is tested. Patch a garbage bearer expecting 401 and no row.
- VG4 the 3s payment client timeout is not what the outage tests run — `medium` — connection-refused uses a hand-built client. Patch a call through the `paymentRestClient` bean to a socket that accepts and does not answer.
- VG5 overlapping inserts of one `orderId` are not tested — `defer` — sequential replay is tested. A real overlapping insert is its own race; recorded in deferred-work.

## Design Notes

The breaker wraps the outbound call, as eligibility-api wraps the credit bureau. A stopped payment process never runs an in-process breaker. `PaymentDeclinedException` is ignored so declines cannot open the circuit. The fallback throws `PaymentUnavailableException` and must not approve the charge.

`@CircuitBreaker` is on `RestPaymentClient`, called by a separate `PaymentGateway` bean, so the annotation crosses a proxy. The saga still depends only on `PaymentGateway`.

The idempotency key is `orderId`. A stored authorization beats a later `simulateDecline`.

## Verification

**Commands:**
- `mvn test` in `services/payment-service` -- expected: exit 0, including success, idempotent retry, and outage with no row
- `mvn test` in `services/order-service` -- expected: exit 0, including decline compensation and outage compensation with no void

**Manual checks (if no CLI):**
- None if those two suites pass.
