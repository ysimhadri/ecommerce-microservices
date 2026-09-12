# Deferred Work

## Done

- source_spec: `_bmad-output/implementation-artifacts/spec-product-catalog-service.md`
  summary: Product Catalog Service — product listings, search, and categories demonstrating a CQRS/read-optimized store with caching.
  evidence: Originally split from the "microservices ecommerce platform" intent as an independently shippable service, deferred behind the foundational User/Auth Service; now built and reviewed.
  status: Built in `services/product-catalog-service`, merged to `main`. See root README for its API table and design-pattern notes.

- summary: API Portal (service-to-service auth) — client-credentials HS256 JWT issuance so microservices can call each other's protected endpoints, plus write-protection for Product Catalog's write endpoints.
  evidence: Product Catalog's writes were open (v1 scope cut, see its README section's history); once a second internal caller needed to write to it, some form of S2S auth became necessary. `auth-service` was explicitly kept human-user-only rather than overloaded into a second responsibility.
  status: Built in `services/api-portal-service` (branch `feat/api-portal-service`) — service registration, producer API declaration, consumer grants (auto-approved, no human workflow), and the `/oauth/token` client-credentials endpoint. `product-catalog-service`'s `POST /categories` and `POST /products` now require a valid token with the `catalog:write` scope; its `GET` endpoints remain public. See root README's `api-portal-service` section and "Service-to-service (S2S) auth flow" for the full picture, including why this is deliberately *not* the API Gateway below.

## Still deferred

- source_spec: none
  summary: Cart Service — shopping cart state management demonstrating session/state-management patterns.
  evidence: Split from the original "microservices ecommerce platform" intent as an independently shippable service; deferred behind the foundational User/Auth Service.

- source_spec: none
  summary: Order Service — checkout and order lifecycle demonstrating the Saga/orchestration pattern across Payment and Inventory.
  evidence: Split from the original "microservices ecommerce platform" intent as an independently shippable service; deferred behind the foundational User/Auth Service.

- source_spec: none
  summary: Payment Service — payment processing demonstrating idempotency and circuit-breaker patterns.
  evidence: Split from the original "microservices ecommerce platform" intent as an independently shippable service; deferred behind the foundational User/Auth Service.

- source_spec: none
  summary: Inventory Service — stock tracking and reservation demonstrating optimistic locking.
  evidence: Split from the original "microservices ecommerce platform" intent as an independently shippable service; deferred behind the foundational User/Auth Service.

- source_spec: none
  summary: Notification Service — order/payment event notifications demonstrating pub/sub, event-driven architecture.
  evidence: Split from the original "microservices ecommerce platform" intent as an independently shippable service; deferred behind the foundational User/Auth Service.

- source_spec: none
  summary: API Gateway — single entry point demonstrating the gateway/BFF pattern with routing and aggregation.
  evidence: Split from the original "microservices ecommerce platform" intent as an independently shippable service; deferred behind the foundational User/Auth Service. NOTE — api-portal-service (above, done) is not this: it issues/validates S2S tokens but does not route, aggregate, or front external traffic. This item is still open.

## Deferred from: code review of spec-product-catalog-service (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-product-catalog-service.md`
  summary: No CI configuration exists for product-catalog-service (or any service in this repo).
  evidence: Pre-existing gap across the whole repo, not introduced by this PR — auth-service has no CI config either. Noted during review, not actionable as a fix to this one PR.
