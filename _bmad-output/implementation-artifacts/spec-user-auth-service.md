---
title: 'User/Auth Service — Foundational Ecommerce Microservice'
type: 'feature'
created: '2026-09-09'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '1e95dfaedc527f70f5ff23a6e09d12e021ae5743'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The ecommerce platform has no services yet — there is no repo layout, no way for a user to register or authenticate, and no established convention (build tooling, containerization, testing) for the microservices that will follow.

**Approach:** Build a standalone User/Auth microservice (register, login, JWT issuance, profile lookup) as the first service in a new `services/` monorepo, using Java 21 + Spring Boot 3.3.x + PostgreSQL, packaged with Docker/docker-compose, and deliberately structured to make several classic design patterns (Repository, DTO, Strategy, Chain of Responsibility, layered architecture) visible in the code for interview-prep purposes.

## Boundaries & Constraints

**Always:**
- One datastore (PostgreSQL) owned by this service alone — no shared DB across future services.
- Passwords hashed with BCrypt (Spring's `PasswordEncoder` abstraction); never store or log plaintext passwords.
- Stateless JWT (HS256, short-lived access token only) for auth; no server-side session state.
- Versioned REST API under `/api/v1/auth/...`.
- Schema managed via Flyway migrations, not `ddl-auto`.
- Runnable end-to-end via `docker compose up --build` (Postgres + service containers).
- Unit tests for service-layer logic and an integration test exercising the real HTTP endpoints.

**Never:**
- Do not build the API Gateway, Cart, Order, Payment, Inventory, Notification, or Catalog services here — each is deferred (see `deferred-work.md`).
- Do not implement OAuth/social login or refresh-token rotation — v1 issues access tokens only.
- Do not add a message broker/event bus — no cross-service events in this spec.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Register success | valid email + password | 201, user persisted, password not echoed back | N/A |
| Register duplicate email | email already exists | 409 Conflict | JSON error body, no stack trace leaked |
| Login success | correct email + password | 200, JWT access token in body | N/A |
| Login bad credentials | wrong password or unknown email | 401 Unauthorized | generic message — no user enumeration |
| Get profile, valid token | `Authorization: Bearer <jwt>` | 200, profile (id, email, createdAt) — no password hash | N/A |
| Get profile, missing/expired/invalid token | bad or absent Authorization header | 401 Unauthorized | N/A |

</frozen-after-approval>

## Code Map

Greenfield — no existing code. This spec creates the repo from scratch:
- `services/auth-service/` -- new Maven/Spring Boot service root
- `docker-compose.yml` -- new, root-level, orchestrates `postgres` + `auth-service`
- `README.md` -- new, root-level, documents monorepo layout for future services

## Tasks & Acceptance

**Execution:**
- [x] `services/auth-service/pom.xml` -- Spring Boot 3.3.x parent; deps: web, data-jpa, validation, security (for `PasswordEncoder` only), postgresql, flyway-core, jjwt (api/impl/jackson), spring-boot-starter-test, Testcontainers-postgresql -- project scaffold
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/AuthServiceApplication.java` -- Spring Boot entrypoint
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/model/User.java` -- JPA entity: id, email (unique), passwordHash, createdAt
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/repository/UserRepository.java` -- `JpaRepository<User, UUID>` with `findByEmail` -- Repository pattern
- [x] `services/auth-service/src/main/resources/db/migration/V1__create_users_table.sql` -- Flyway migration for `users` table
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/dto/` -- `RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserProfileResponse` -- DTOs kept separate from `User` entity
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/security/PasswordEncoderConfig.java` -- exposes `BCryptPasswordEncoder` as the `PasswordEncoder` bean -- Strategy pattern
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/security/JwtService.java` -- signs/validates HS256 JWTs, short expiry (config-driven)
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/security/JwtAuthFilter.java` -- `OncePerRequestFilter` validating Bearer tokens into `SecurityContext` -- Chain of Responsibility
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/security/SecurityConfig.java` -- filter chain: permits `/register` + `/login`, secures `/me`, registers `JwtAuthFilter`
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/service/AuthService.java` -- register (hash+persist, reject duplicate email), login (verify + issue JWT), getProfile -- service layer
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/controller/AuthController.java` -- `POST /register`, `POST /login`, `GET /me` -- API layer
- [x] `services/auth-service/src/main/java/com/ecommerce/auth/exception/DuplicateEmailException.java` + `GlobalExceptionHandler.java` -- `@ControllerAdvice` mapping domain exceptions to HTTP status -- centralized error handling
- [x] `services/auth-service/src/main/resources/application.yml` -- datasource/JPA/Flyway/JWT config, all overridable via env vars
- [x] `services/auth-service/Dockerfile` -- multi-stage build (Maven build stage → slim JRE runtime stage)
- [x] `docker-compose.yml` -- `postgres` + `auth-service` on a shared network; establishes the convention future services will join
- [x] `services/auth-service/src/test/java/.../AuthServiceTest.java` -- unit tests: register success/duplicate, login success/bad-credentials
- [x] `services/auth-service/src/test/java/.../AuthControllerIntegrationTest.java` -- Testcontainers-backed integration test covering all six I/O matrix rows end-to-end
- [x] `README.md` -- monorepo layout (`services/<name>`), how to run `docker compose up --build`, links deferred services from `deferred-work.md`
- [x] `.gitignore` -- standard Java/Maven/IDE ignores; also `git init` the repo if not already one (repo was already a git repository)

**Acceptance Criteria:**
- Given the compose stack is running, when a client registers, logs in, then calls `GET /me` with the returned token, then the profile matches the registered email and never includes the password hash.
- Given the service restarts, when it boots, then Flyway migrations apply idempotently and prior data in the Postgres volume survives.
- Given a request to `/me` with no `Authorization` header, when it hits the filter chain, then the response is 401.

## Design Notes

Patterns to make explicit (via code comments/README callouts, not just incidental structure) since demonstrating them is the point of this project:
- **Repository** — `UserRepository` isolates persistence from `AuthService`.
- **DTO** — request/response DTOs never leak the `User` entity or password hash across the API boundary.
- **Strategy** — `PasswordEncoder` is an interface; `BCryptPasswordEncoder` is the swappable implementation.
- **Chain of Responsibility** — `JwtAuthFilter` sits in the Spring Security filter chain.
- **Layered architecture** — Controller → Service → Repository, each single-responsibility.
- **Centralized error handling** — `GlobalExceptionHandler` maps domain exceptions to HTTP responses in one place instead of per-controller try/catch.

## Verification

**Commands:**
- `cd services/auth-service && mvn test` -- expected: all unit + integration tests pass
- `docker compose up --build` -- expected: `postgres` and `auth-service` containers report healthy; service reachable at `http://localhost:8080/api/v1/auth/register`

**Manual checks (if no CLI):**
- `curl -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{"email":"a@b.com","password":"secret123"}'` → 201, then `POST /login` with same creds → 200 + JWT, then `GET /me` with `Authorization: Bearer <jwt>` → 200 with matching profile, no password hash present.

## Implementation Notes

Implemented as specified. All acceptance criteria verified against a real `docker compose up --build` stack (see below); all 15 automated tests (7 unit + 8 Testcontainers integration) pass via `mvn test`.

**Verification performed:**
- `mvn test` from `services/auth-service`: 15/15 tests pass (`AuthServiceTest`: register success/duplicate/normalization, login success/wrong-password/unknown-email, getProfile; `AuthControllerIntegrationTest`: all six I/O matrix rows against a real Testcontainers PostgreSQL, plus a malformed-token case).
- `docker compose up --build`: both `postgres` and `auth-service` reported `healthy`.
- Manual curl flow exactly as specified: register (201) → duplicate register (409) → login (200 + JWT) → bad-password login (401) → `GET /me` with token (200, matching profile, no password hash) → `GET /me` without token (401). All responses matched the I/O matrix.
- Restarted the `auth-service` container: Flyway logged "Schema 'public' is up to date. No migration necessary." (idempotent), and the previously-registered user was still present (login succeeded, re-register returned 409) — confirms the Postgres volume persists data across restarts.
- `docker compose down -v` used to tear everything down cleanly afterward.

**Bug found and fixed during verification:** the initial `User` entity used Hibernate's `@CreationTimestamp` for `createdAt`. That annotation only populates the field on the in-memory entity at flush time, which is *after* `AuthService.register` already builds the `UserProfileResponse` — so the register endpoint returned `"createdAt": null`. Fixed by setting `createdAt = Instant.now()` directly in the `User` constructor instead, so it's present immediately after `save()`. Covered by an integration-test-visible symptom; re-verified via curl and `mvn test` after the fix.

**Notable implementation choices not spelled out in the spec:**
- Emails are normalized (trimmed + lower-cased) before lookups/inserts, so registration/login aren't case-sensitive on email; a unit test covers this.
- Login failures (wrong password *and* unknown email) both throw Spring Security's `BadCredentialsException`, mapped by `GlobalExceptionHandler` to the same 401 body — this is what the "no user enumeration" requirement is built on, rather than a bespoke exception type.
- `docker-compose.yml`'s `auth-service` healthcheck uses a plain `wget` GET against a POST-only route (`/api/v1/auth/login`) and only fails on wget's "could not connect" exit code (4) — since the service intentionally has no Actuator dependency, this proves the HTTP port is listening without adding an unrequested dependency.
- The integration test's `TestRestTemplate` had to be reconfigured to use Apache HttpClient5 (`httpclient5` added as a test-scope dependency) instead of the JDK default, because the JDK's `HttpURLConnection`-based client cannot replay a POST body after a 401 response ("cannot retry due to server authentication, in streaming mode") — several tests deliberately provoke a 401 on `POST /login`.

**Left incomplete / risks for follow-up:**
- No refresh-token/OAuth flows, message broker, or other services — all as the spec's "Never" list requires.
- JWT secret has a dev-only default in `application.yml`/`docker-compose.yml`; it must be overridden via `JWT_SECRET` in any non-local environment (already env-overridable, just not enforced/validated at startup).
- No rate limiting on `/login` or `/register` — out of scope per the spec, but worth flagging for a future hardening pass.

**Post-review patch round (step-04, orchestrator):** all 8 `patch`-routed findings fixed by the same implementation subagent (see Review Triage Log). Re-verified independently: `mvn test` → 18/18 pass; live `docker compose up --build` flow re-run by hand, confirming each fix — duplicate registration now 409 (was a race risk), lowercase `bearer <token>` now authenticates, malformed JSON body now 400 `VALIDATION_ERROR`, `expiresInSeconds: 900` confirms the single de-duplicated config default still applies, and `nc -z localhost 5432` confirms Postgres's port is no longer published to the host. Stack torn down with `docker compose down -v` after.

**Independent re-verification (step-03, orchestrator):** re-ran `mvn test` myself with JDK 21 (not the subagent's report) against the real diff since `baseline_commit`: 15/15 tests pass (8 Testcontainers integration + 7 unit), 0 failures/errors. All 20 execution tasks confirmed present and matching in the diff. Matrix audit: all 6 I/O rows have a passing covering test; the "expired/invalid" token row is exercised via a malformed-token test rather than a literally-expired one, but both share the identical `JwtException` catch path in `JwtAuthFilter` — accepted as adequate coverage of the row's stated 401 behavior rather than a gap (later strengthened by a dedicated expired-token test after review, see Review Triage Log).

## Spec Change Log

## Review Triage Log

- **[medium → patch]** `AuthService.register`: check-then-act race (`existsByEmail` then `save`) — concurrent duplicate registrations can both pass the check; the losing `save()` throws an uncaught `DataIntegrityViolationException`, falling to the catch-all handler → `500` instead of the spec's required `409`. Verified by reading `AuthService.java`/`GlobalExceptionHandler.java`: no handler for that exception type exists. (blind-hunter, edge-case-hunter, verification-gap all raised this independently — one entry.)
- **[medium → patch]** `GlobalExceptionHandler`: malformed/unparseable JSON body or wrong Content-Type on `/register`/`/login` isn't mapped to `HttpMessageNotReadableException`, so it falls to the catch-all → `500` instead of `400`. Verified: no such handler present in the diff. (edge-case-hunter)
- **[low → patch]** `JwtAuthFilter`: `Authorization` scheme check (`startsWith("Bearer ")`) is case-sensitive; RFC 7235 auth-scheme names are case-insensitive, so a compliant client sending `bearer <token>` is wrongly treated as unauthenticated. Verified in code; fix is a one-line case-insensitive comparison. (edge-case-hunter)
- **[low → patch]** `docker-compose.yml`: `postgres` publishes `5432` to the host, which `auth-service` doesn't need (reaches it over `ecommerce-net`) — needless exposure/port-collision risk. Fix is a deletion. (blind-hunter)
- **[low → patch]** `docker-compose.yml`/`application.yml`: `JWT_EXPIRATION_MS` default (`900000`) is duplicated as a literal in both files; if one changes without the other, compose vs. standalone runs silently diverge. Fix is removing the redundant default. (blind-hunter)
- **[medium → patch]** `AuthControllerIntegrationTest.getMe_withValidToken_...`: only asserts `createdAt` key presence (`containsKeys`), not its value — this already let a real bug (null `createdAt`, see Implementation Notes) through undetected once and would again. Pre-verified gap finding, evidence trusted as filed. (verification-gap)
- **[medium → patch]** No test exercises the `@Valid` bean-validation rejection path (`400`/`VALIDATION_ERROR`) on `/register` or `/login` — every test in the diff sends well-formed payloads. Pre-verified gap finding. (blind-hunter, verification-gap — one entry.)
- **[medium → patch]** No test constructs a real, validly-signed-but-expired JWT; the only "invalid token" test uses a malformed string, which fails at signature parsing and never reaches the expiry branch. Pre-verified gap finding; verification-gap filed `defer`, but the fix (mint a token via `JwtService` with a negative/zero expiration) is a bounded test-only addition with no product-code change, so routed `patch` instead. (blind-hunter, edge-case-hunter, verification-gap — one entry.)
- **[false]** README links to `_bmad-output/implementation-artifacts/deferred-work.md`, claimed as dangling since the diff doesn't create it. Refuted: that file was committed in the baseline commit (`1e95dfa`, before this diff), confirmed via `git log`. Not dangling. (blind-hunter)
- **[false]** `AuthService.getProfile` returns a login-style "Invalid email or password" message when a JWT resolves to a missing user, described as confusing for a deleted-user edge case. Refuted: this service exposes no delete/deactivate-user operation anywhere in its API surface, so the branch is unreachable except via direct out-of-band DB manipulation, outside what the system itself can do. (blind-hunter)
- **[low → rejected]** `RegisterRequest.email` has no `@Size(max=255)` bound matching the DB column, so an oversized email would 500 instead of 400 at the DB layer. Real, but unlikely in everyday use (valid emails are ≤254 chars) and the fix adds a validation guard — rejected per the low+guard-fix rule. (blind-hunter)
- **[low → rejected]** `LoginRequest.password` has no `@Size` cap, allowing arbitrarily large passwords through to BCrypt on an already rate-limit-free endpoint. Real, but unlikely in everyday use and the fix adds a validation guard — rejected per the low+guard-fix rule; broader rate-limiting is already a named, accepted follow-up in Implementation Notes. (blind-hunter)
- **[low → rejected]** JWT secret shorter than 32 bytes when overridden via `JWT_SECRET` causes an uncaught `WeakKeyException` at startup instead of a clear error. Real, but only bites an operator during initial misconfiguration and the fix adds a validation guard — rejected per the low+guard-fix rule. (edge-case-hunter)
- **[low → rejected]** `JWT_EXPIRATION_MS` overridden with a non-positive or overflow-inducing value causes token-lifetime or `DateTimeException` misbehavior. Real, but only bites an operator during misconfiguration and the fix adds a validation guard — rejected per the low+guard-fix rule. (edge-case-hunter)
- **[low → rejected]** README documents only happy-path responses, not the `ErrorResponse` shape/codes. Real gap, but cosmetic and unlikely to block anyone reading `GlobalExceptionHandler` directly — rejected as low-value documentation polish. (blind-hunter)
- **[low → rejected]** Email normalization is unit-tested for `register` but not `login`. Real gap, but both call the identical private `normalize()` method already proven by the register-path test — negligible incremental risk reduction. (blind-hunter)
- **[low → self-corrected, not routed]** Implementation Notes said "19 execution tasks confirmed present" — the diff actually shows 20. Real clerical error, but its only fix is editing this build's own spec text, which is excluded from patch/defer routing by rule; corrected directly above instead of dispatching. (blind-hunter)
