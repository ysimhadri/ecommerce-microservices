---
title: 'Product Catalog Service — Categories, Products, Search (CQRS-lite + Caching)'
type: 'feature'
created: '2026-09-12'
status: 'done'
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
- `POST /products` with an unknown `categoryId` returns 400, but `GET /products?categoryId=<unknown>` silently returns an empty page — asymmetric, not stated as intentional anywhere in code or docs (not raised by review; left as-is).
- No upper bound on caller-supplied page `size`, `q` length, or `price` precision, and no update/delete endpoints exist — all reviewed and accepted as low-severity/intentional (see Review Triage Log).

**Post-review patch round (2026-09-12):** all 7 `patch`-routed findings from the 4-layer review fixed directly (no live implementation agent for this externally-built branch):
1. **High — concurrent-duplicate race escaping 409.** `Category` now implements `Persistable<UUID>`; `CategoryCommandService.create` uses `saveAndFlush(...)` instead of `save(...)` — identical fix to the one applied to `auth-service`'s `User`/`AuthService` the same day, for the same underlying JPA behavior (client-assigned UUID ids default to `merge()` with a deferred flush). Added `ConcurrentCategoryCreationTest` (8-thread race, real Postgres) as a permanent regression test; empirically confirmed broken before the fix (0/7 caught), fixed after (7/7 caught).
2. **Case-insensitive uniqueness vs. case-sensitive DB constraint.** Added `V3__case_insensitive_category_name_uniqueness.sql` — a functional unique index on `lower(name)`.
3. **Type-mismatch inputs falling to 500.** Added `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` to `GlobalExceptionHandler` → 400 `VALIDATION_ERROR`.
4. **Unescaped LIKE wildcards in search.** `ProductQueryService` now escapes `%`, `_`, and `\` in the search term before it reaches `ProductRepository`'s native queries, which now pair `ILIKE` with `ESCAPE '\'`. Added a test proving a literal `%`/`_` in a search term is matched literally, not as a wildcard.
5. **Combined `categoryId`+`q` filter untested against a real DB.** Added `searchProducts_byCategoryAndTerm_returnsOnlyProductsMatchingBothFilters` to the Testcontainers integration suite.
6. **`deferred-work.md` schema drift.** Restored the `source_spec` field on the "Done" entry and refreshed its stale evidence text.
7. **Scope creep.** Removed `docs/landing-changes-from-assistant.md` from this branch (PR-mechanics documentation, unrelated to the catalog feature).

Full suite after patches: 24/24 pass (`cd services/product-catalog-service && mvn test`).

## Spec Change Log

## Review Triage Log

Reviewed 2026-09-12 via 4 layers (Blind Hunter, Edge Case Hunter, Verification Gap, Acceptance Auditor) against `origin/feat/product-catalog-service`. None applied yet — session paused before the apply step.

- **[high → patch]** `CategoryCommandService.create`'s concurrent-duplicate catch is dead code: `Category.id` is a client-assigned UUID (no `@GeneratedValue`/`Persistable`), so `save()` defers its INSERT to flush/commit time, after the try/catch has already returned. **Empirically confirmed** (8-thread race, real Postgres): 1 success, 0/7 caught as 409, all 7 raw `DataIntegrityViolationException`. Identical bug to the one just fixed in `auth-service` (see its spec's Implementation Notes, 2026-09-12) — same fix applies: `Category implements Persistable<UUID>` + `categoryRepository.saveAndFlush(...)`. (acceptance-auditor + verification-gap)
- **[medium → patch]** Case-insensitive uniqueness check (`existsByNameIgnoreCase`) is undermined by a case-*sensitive* DB constraint (`V1__create_categories_table.sql`, plain `UNIQUE(name)`) — two concurrent creates differing only in case can both succeed. Fix: functional unique index on `lower(name)` (new Flyway migration) or a `citext` column. (acceptance-auditor + blind-hunter + edge-case-hunter)
- **[medium → patch]** No `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` in `GlobalExceptionHandler` — a non-UUID `GET /products/{id}` path segment or `?categoryId=` query param falls to the catch-all → 500 instead of 400. Fix: add the handler, mirroring the existing `VALIDATION_ERROR` shape. (blind-hunter + edge-case-hunter)
- **[medium → patch]** `ProductRepository.searchByTerm`/`searchByCategoryIdAndTerm` build native `ILIKE CONCAT('%', :term, '%')` queries without escaping `%`/`_` in the user-supplied term — a literal `%` or `_` in a search is silently treated as a wildcard, producing wrong matches. Fix: escape both characters in `:term` before binding (e.g. `term.replace("%","\\%").replace("_","\\_")`, with an `ESCAPE` clause). (blind-hunter + edge-case-hunter)
- **[medium → patch]** Combined `categoryId`+`q` filter (`ProductRepository.searchByCategoryIdAndTerm`) never runs against a real database in the test suite — only mocked in `ProductServiceTest`, and the integration test only exercises each filter alone. Fix: add an integration-test case supplying both params against the real Testcontainers Postgres. (verification-gap, pre-verified)
- **[low → patch]** `deferred-work.md`'s "Done" entry for this service drops the `source_spec: none` field every "Still deferred" entry retains — schema inconsistency for anything that parses this file uniformly. Fix: add the field back. (blind-hunter + edge-case-hunter)
- **[low → patch]** `docs/landing-changes-from-assistant.md` documents this specific PR's push mechanics (bundle-to-Mac workaround, a named developer's machine/GitHub handle, hardcoded commit hash) — unrelated to the catalog feature, will go stale, reads as scope creep in a feature PR. Fix: drop it from this PR (or relocate to a repo-wide ops-notes location if the content is worth keeping). (blind-hunter)
- **[false]** No update/delete endpoints, called out as an unexplained gap unlike the no-auth cut. Refuted: this spec's frozen "Never" boundary explicitly documents it as intentional v1 scope (`POST`(create) + `GET`(read) only). (blind-hunter)
- **[false]** No actuator/health endpoint, called out as a missing-dependency gap. Refuted: this spec's frozen "Never" boundary explicitly documents it as intentional v1 scope. (blind-hunter)
- **[low → rejected]** `q` search param has no max length; `ProductCreateRequest.description` has no `@Size` cap (unlike `CategoryCreateRequest.description`'s 500-char cap). Real, but unlikely in everyday use and the fix adds a validation guard — rejected per the low+guard-fix rule, same standard applied to auth-service. (blind-hunter)
- **[low → rejected]** No upper bound on caller-supplied page `size`. Same low+guard-fix rejection as the analogous auth-service finding. (blind-hunter)
- **[low → rejected]** `price` has no `@Digits` cap matching the DB's `NUMERIC(12,2)`; only triggers on a price with >10 integer digits (>$99,999,999,999) — real but requires an extreme/adversarial value, and the fix adds a guard. Rejected. (blind-hunter + edge-case-hunter)
- **[low → rejected]** One Caffeine cache spec (`maximumSize=500, expireAfterWrite=5m`) applied identically to both `categories` and `products` caches despite different cardinality profiles. Real tuning nitpick; fix (per-cache config) is more than a direct correction, and generous enough for this project's scale to be low priority. Rejected. (blind-hunter)
- **[low → rejected]** README's generic CQRS-lite description ("command side evicts on every write") doesn't hold for products — verified: `ProductCommandService` has no `@CacheEvict`, but its own javadoc correctly and deliberately explains why (products are cached by id on first read, so a create is just a future cache-miss, never stale data). The code itself is correct and well-documented; only a generic summary table oversimplifies. Cosmetic, rejected. (edge-case-hunter)
- **[defer]** No CI configuration for the new service (no `.github/workflows` touched). Pre-existing gap across the whole repo (auth-service has none either), not introduced by this PR. (blind-hunter)
