# Deferred Work

## Done

- summary: Product Catalog Service — product listings, search, and categories demonstrating a CQRS/read-optimized store with caching.
  evidence: Split from the original "microservices ecommerce platform" intent as an independently shippable service; deferred behind the foundational User/Auth Service.
  status: Built in `services/product-catalog-service` (branch `feat/product-catalog-service`). See root README for its API table and design-pattern notes.

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
  evidence: Split from the original "microservices ecommerce platform" intent as an independently shippable service; deferred behind the foundational User/Auth Service.
