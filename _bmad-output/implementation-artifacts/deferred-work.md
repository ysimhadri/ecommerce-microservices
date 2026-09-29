# Deferred Work

## Done

- source_spec: `_bmad-output/implementation-artifacts/spec-product-catalog-service.md`
  summary: Product Catalog Service — product listings, search, and categories demonstrating a CQRS/read-optimized store with caching.
  evidence: Originally split from the "microservices ecommerce platform" intent as an independently shippable service, deferred behind the foundational User/Auth Service; now built and reviewed.
  status: Built in `services/product-catalog-service`, merged to `main`. See root README for its API table and design-pattern notes.

- summary: API Portal (service-to-service auth) — client-credentials HS256 JWT issuance so microservices can call each other's protected endpoints, plus write-protection for Product Catalog's write endpoints.
  evidence: Product Catalog's writes were open (v1 scope cut, see its README section's history); once a second internal caller needed to write to it, some form of S2S auth became necessary. `auth-service` was explicitly kept human-user-only rather than overloaded into a second responsibility.
  status: Built in `services/api-portal-service` (branch `feat/api-portal-service`) — service registration, producer API declaration, consumer grants (auto-approved, no human workflow), and the `/oauth/token` client-credentials endpoint. `product-catalog-service`'s `POST /categories` and `POST /products` now require a valid token with the `catalog:write` scope; its `GET` endpoints remain public. See root README's `api-portal-service` section and "Service-to-service (S2S) auth flow" for the full picture, including why this is deliberately *not* the API Gateway below.

- source_spec: `_bmad-output/implementation-artifacts/spec-cart-order-saga.md`
  summary: Cart Service — per-user carts that an in-process order saga can lock, clear, and restore without dropping lines.
  evidence: Built as part of placing an order from a cart. Clear hides lines (`CHECKED_OUT`) and restore returns the same `ACTIVE` lines. See `services/cart-service`.
  status: Built in `services/cart-service`.

- source_spec: `_bmad-output/implementation-artifacts/spec-cart-order-saga.md`
  summary: Order Service — checkout from a cart with an in-process saga (mock payment, not a payment provider).
  evidence: `OrderSagaOrchestrator` calls cart, inventory, and catalog over HTTP and compensates completed steps in reverse inside the same request. No broker and no crash-recovery job. See `services/order-service`.
  status: Built in `services/order-service`.

- source_spec: `_bmad-output/implementation-artifacts/spec-cart-order-saga.md`
  summary: Inventory Service — minimal stock and all-or-nothing reservations with optimistic locking, enough for the order saga to reserve and release.
  evidence: Catalog still has no stock. Hold, release, commit, and revert live in `services/inventory-service`. This is not a broader inventory product.
  status: Built in `services/inventory-service`.

## Still deferred

- source_spec: none
  summary: Payment Service — a real payment provider, with idempotency and circuit-breaker patterns.
  evidence: Split from the original "microservices ecommerce platform" intent. `order-service` only has `MockPaymentGateway` (`simulatePaymentFailure`). No capture, no provider, no circuit breaker.

- source_spec: none
  summary: Notification Service — order/payment event notifications demonstrating pub/sub, event-driven architecture.
  evidence: Split from the original "microservices ecommerce platform" intent as an independently shippable service; deferred behind the foundational User/Auth Service. The order saga does not publish events.

- source_spec: none
  summary: API Gateway — single entry point demonstrating the gateway/BFF pattern with routing and aggregation.
  evidence: Split from the original "microservices ecommerce platform" intent as an independently shippable service; deferred behind the foundational User/Auth Service. NOTE — api-portal-service (above, done) is not this: it issues/validates S2S tokens but does not route, aggregate, or front external traffic. This item is still open.

## Deferred from: code review of spec-product-catalog-service (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-product-catalog-service.md`
  summary: No CI configuration exists for product-catalog-service (or any service in this repo).
  evidence: Pre-existing gap across the whole repo, not introduced by this PR — auth-service has no CI config either. Noted during review, not actionable as a fix to this one PR.

## Deferred from: code review of spec-api-portal-service (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-api-portal-service.md`
  summary: JWT secret rotation has no kid/dual-key support for a graceful rotation window.
  evidence: PORTAL_JWT_SECRET must be updated identically and simultaneously across api-portal-service and every producer service today; a real operational gap, but adding key-id-based dual-key support is an architectural enhancement beyond a patch-sized fix.
