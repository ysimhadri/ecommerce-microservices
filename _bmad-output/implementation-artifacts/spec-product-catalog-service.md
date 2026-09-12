---
title: 'Product Catalog Service — Categories, Products, Search (CQRS-lite + Caching)'
type: 'feature'
created: '2026-09-12'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'a93b1588277e855d579cb79a56fde973346386c1'
retroactive: true
implemented_on: 'origin/feat/product-catalog-service (commit b8cb14b)'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The monorepo's only service was `auth-service` (identity/JWT). There was no place to store or browse product/category data — an ecommerce platform needs a catalog before it can have a cart, order, or inventory.

**Approach:** A second independently-deployable Spring Boot service, owning its own PostgreSQL database (`catalogdb`), exposing category and product create+read endpoints and a paged/filterable/searchable product list. Built to mirror `auth-service`'s layered conventions while introducing a CQRS-lite split (separate Command/Query service per entity) and an in-process read cache (Caffeine) on the hottest reads. v1 intentionally ships with no authentication — deferred to a later service.

**Note:** This spec is written retroactively against code already implemented on `origin/feat/product-catalog-service` (not built via this workflow) so it can go through the same spec-anchored review as the rest of this repo before merging to `main`. It describes the system as built; known issues are carried forward to the Review Triage Log rather than silently fixed here.

## Boundaries & Constraints

**Always:**
- Every endpoint lives under `/api/v1/catalog/...`.
- Category names are unique, case-insensitively (app-level `existsByNameIgnoreCase` check, with a DB-unique-constraint-driven fallback for the concurrent-create race).
- A product must reference an existing `categoryId` at creation time (app check + DB `FOREIGN KEY`).
- `price` must be `>= 0` (DTO validation + DB `CHECK` constraint).
- Every error response is the uniform `ErrorResponse{code, message, timestamp}` shape; no stack trace or internal detail reaches the client.
- Schema is Flyway-only (`hibernate.ddl-auto: validate`); Hibernate never generates DDL.
- One datastore (`catalogdb`), never shared with `auth-service`'s `authdb`.
- Controllers never return JPA entities — only DTO records.
- The category list and get-product-by-id reads are cached (Caffeine, 5 min TTL); every category write evicts the category cache.

**Never:**
- No authentication/authorization on any catalog endpoint in this spec's scope (addressed separately — see `spec-api-portal-service.md`).
- No update or delete endpoints for either entity — only `POST` (create) + `GET` (read).
- No caching of the product list/search/filter endpoint (too many distinct filter/page/search-term combinations to cache usefully).
- No event publishing/consuming of catalog-change events.
- No actuator dependency / health endpoint.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Create category success | valid `name` (≤150) + optional `description` (≤500) | 201, `CategoryResponse` | N/A |
| Create category duplicate name | name already exists (case-insensitive) | 409 `CATEGORY_ALREADY_EXISTS` | JSON error body |
| Create category validation error | blank/oversized name, oversized description | 400 `VALIDATION_ERROR` | field-level message |
| List categories | — | 200, full list (cached) | N/A |
| Create product success | valid name/price/existing categoryId | 201, `ProductResponse` with resolved `categoryName` | N/A |
| Create product unknown category | `categoryId` doesn't reference an existing category | 400 `INVALID_CATEGORY_ID` | JSON error body |
| Create product validation error | blank/oversized name, negative or missing price, missing categoryId | 400 `VALIDATION_ERROR` | field-level message |
| Get product by id, known | valid UUID | 200, `ProductResponse` (cached) | N/A |
| Get product by id, unknown | valid UUID, no such product | 404 `PRODUCT_NOT_FOUND` | JSON error body |
| List/search products | optional `categoryId`, `q`, paging | 200, `PageResponse<ProductResponse>` — branches by which filters are present | N/A |

</frozen-after-approval>

## Code Map

Already implemented — paths below are as they exist on `origin/feat/product-catalog-service`, under `services/product-catalog-service/src/main/java/com/ecommerce/catalog/`:

- `CatalogServiceApplication.java` -- entry point; doc comment states the no-auth v1 scope cut
- `config/CacheConfig.java` -- `@EnableCaching` toggle
- `controller/CategoryController.java` -- `POST /categories`, `GET /categories`
- `controller/ProductController.java` -- `POST /products`, `GET /products/{id}`, `GET /products` (filter/search/paged)
- `dto/*.java` -- `CategoryCreateRequest`, `CategoryResponse`, `ProductCreateRequest`, `ProductResponse`, `PageResponse` (stable paged envelope, decoupled from Spring Data `Page` serialization)
- `exception/*.java` -- `DuplicateCategoryNameException` (409), `InvalidCategoryReferenceException` (400), `ProductNotFoundException` (404), `ErrorResponse`, `GlobalExceptionHandler`
- `model/Category.java`, `model/Product.java` -- JPA entities; `Product.categoryId` is a plain FK column, not `@ManyToOne` (avoids lazy-loading/N+1)
- `repository/CategoryRepository.java`, `repository/ProductRepository.java` -- Spring Data repos; `ProductRepository` adds native `ILIKE` search queries tuned for `pg_trgm` GIN indexes
- `service/CategoryCommandService.java`, `service/CategoryQueryService.java` -- CQRS-lite write/read split for categories
- `service/ProductCommandService.java`, `service/ProductQueryService.java` -- CQRS-lite write/read split for products; query side does batch category-name resolution to avoid N+1
- `resources/application.yml`, `resources/db/migration/V1__create_categories_table.sql`, `V2__create_products_table.sql` -- config + schema
- `Dockerfile`, `pom.xml` -- Spring Boot 3.3.4, Java 21, standard multi-stage build

## Tasks & Acceptance

**Execution (all already implemented on `feat/product-catalog-service`, commit `b8cb14b`):**
- [x] `services/product-catalog-service/pom.xml` -- Spring Boot 3.3.4 parent; web, data-jpa, validation, cache, caffeine, postgresql, flyway, testcontainers
- [x] `.../CatalogServiceApplication.java` -- entry point
- [x] `.../model/Category.java`, `.../model/Product.java` -- JPA entities
- [x] `.../repository/CategoryRepository.java`, `.../repository/ProductRepository.java` -- Repository pattern
- [x] `.../db/migration/V1__create_categories_table.sql`, `V2__create_products_table.sql` -- Flyway schema
- [x] `.../dto/*.java` -- request/response DTOs, kept separate from entities
- [x] `.../config/CacheConfig.java` -- caching enabled
- [x] `.../service/Category{Command,Query}Service.java`, `.../service/Product{Command,Query}Service.java` -- CQRS-lite business logic
- [x] `.../controller/CategoryController.java`, `.../controller/ProductController.java` -- API layer
- [x] `.../exception/*.java` -- centralized error handling
- [x] `.../resources/application.yml` -- datasource/JPA/Flyway/cache config
- [x] `services/product-catalog-service/Dockerfile`, `docker-compose.yml` entry -- containerization
- [x] `.../test/.../CategoryServiceTest.java`, `ProductServiceTest.java` -- unit tests
- [x] `.../test/.../CatalogControllerIntegrationTest.java` -- Testcontainers integration tests

**Acceptance Criteria:**
- Given a category exists, when a product is created referencing it, then `GET /products/{id}` returns that product with the category's current name resolved.
- Given two concurrent `POST /categories` requests with the same name, when both execute, then exactly one succeeds (201) and the other receives 409 — not a 500 or a silent duplicate.
- Given `GET /products` is called with no filters, when the catalog has products, then every product is returned, paged.

## Design Notes

Patterns made explicit in the code (see `README.md`'s per-service table too):
- **Layered architecture** — Controller → `*CommandService`/`*QueryService` → Repository.
- **CQRS-lite** — writes/cache-eviction vs. reads/caching split per entity, distinct from `auth-service`'s single-service-per-entity shape.
- **Repository**, **DTO**, **Centralized error handling** — same conventions as `auth-service`.
- **Caching** — `@Cacheable`/`@CacheEvict` via Spring's AOP proxies, Caffeine-backed.

Deliberately **not** present (consistent with "no auth in v1"): no Strategy (`PasswordEncoder`-equivalent) or Chain of Responsibility (`JwtAuthFilter`-equivalent) — both appear in `auth-service` but have no analog here.

## Verification

**Commands:**
- `cd services/product-catalog-service && mvn test` -- expected: all unit + integration tests pass
- `docker compose up --build` -- expected: `catalog-postgres` and `product-catalog-service` containers healthy

## Implementation Notes

Code already exists on `origin/feat/product-catalog-service`. This spec was authored retroactively (2026-09-12) from a full-code investigation (not just the diff) to anchor a structured review before merge. Known gaps surfaced during investigation — carried to review rather than fixed here:
- Non-UUID path/query values (`GET /products/{id}`, `categoryId` filter, `page`/`size`) fall through to the generic `500` handler instead of `400` — no `MethodArgumentTypeMismatchException` handler exists.
- The case-insensitive duplicate-name race guard relies on a case-*sensitive* DB unique constraint, so two concurrent creates differing only in case can both succeed.
- `POST /products` with an unknown `categoryId` returns 400, but `GET /products?categoryId=<unknown>` silently returns an empty page — asymmetric, not stated as intentional anywhere in code or docs.
- No upper bound on caller-supplied page `size`.
- Malformed JSON body, oversized-but-valid-type inputs (name at/over length limits), and the concurrent-duplicate race path have no covering test.

## Spec Change Log

## Review Triage Log
