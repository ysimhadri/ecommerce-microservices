---
title: 'User/Auth Service — Foundational Ecommerce Microservice'
type: 'feature'
created: '2026-09-09'
status: 'ready-for-dev'
route: 'dispatch'
review_loop_iteration: 0
context: []
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
- [ ] `services/auth-service/pom.xml` -- Spring Boot 3.3.x parent; deps: web, data-jpa, validation, security (for `PasswordEncoder` only), postgresql, flyway-core, jjwt (api/impl/jackson), spring-boot-starter-test, Testcontainers-postgresql -- project scaffold
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/AuthServiceApplication.java` -- Spring Boot entrypoint
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/model/User.java` -- JPA entity: id, email (unique), passwordHash, createdAt
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/repository/UserRepository.java` -- `JpaRepository<User, UUID>` with `findByEmail` -- Repository pattern
- [ ] `services/auth-service/src/main/resources/db/migration/V1__create_users_table.sql` -- Flyway migration for `users` table
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/dto/` -- `RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserProfileResponse` -- DTOs kept separate from `User` entity
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/security/PasswordEncoderConfig.java` -- exposes `BCryptPasswordEncoder` as the `PasswordEncoder` bean -- Strategy pattern
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/security/JwtService.java` -- signs/validates HS256 JWTs, short expiry (config-driven)
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/security/JwtAuthFilter.java` -- `OncePerRequestFilter` validating Bearer tokens into `SecurityContext` -- Chain of Responsibility
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/security/SecurityConfig.java` -- filter chain: permits `/register` + `/login`, secures `/me`, registers `JwtAuthFilter`
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/service/AuthService.java` -- register (hash+persist, reject duplicate email), login (verify + issue JWT), getProfile -- service layer
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/controller/AuthController.java` -- `POST /register`, `POST /login`, `GET /me` -- API layer
- [ ] `services/auth-service/src/main/java/com/ecommerce/auth/exception/DuplicateEmailException.java` + `GlobalExceptionHandler.java` -- `@ControllerAdvice` mapping domain exceptions to HTTP status -- centralized error handling
- [ ] `services/auth-service/src/main/resources/application.yml` -- datasource/JPA/Flyway/JWT config, all overridable via env vars
- [ ] `services/auth-service/Dockerfile` -- multi-stage build (Maven build stage → slim JRE runtime stage)
- [ ] `docker-compose.yml` -- `postgres` + `auth-service` on a shared network; establishes the convention future services will join
- [ ] `services/auth-service/src/test/java/.../AuthServiceTest.java` -- unit tests: register success/duplicate, login success/bad-credentials
- [ ] `services/auth-service/src/test/java/.../AuthControllerIntegrationTest.java` -- Testcontainers-backed integration test covering all six I/O matrix rows end-to-end
- [ ] `README.md` -- monorepo layout (`services/<name>`), how to run `docker compose up --build`, links deferred services from `deferred-work.md`
- [ ] `.gitignore` -- standard Java/Maven/IDE ignores; also `git init` the repo if not already one

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

## Spec Change Log

## Review Triage Log
