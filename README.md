# Ecommerce Microservices Monorepo

This repo hosts the services of an ecommerce platform, one directory per
service:

```
services/
  auth-service/   # User/Auth — register, login, JWT issuance, profile lookup
```

Each service:
- owns exactly one datastore (no shared database across services),
- is independently buildable and runnable via Docker,
- exposes a versioned REST API under `/api/v1/<service>/...`.

New services are added the same way `auth-service` was: a new
`services/<name>/` directory, its own Dockerfile, and an entry in
`docker-compose.yml`.

## Services

### auth-service

The foundational service — every other service will eventually depend on
the identities it issues tokens for. See
[`services/auth-service`](services/auth-service) for its source.

Stack: Java 21, Spring Boot 3.3.x, PostgreSQL, Flyway, JWT (HS256, access
token only — no refresh tokens, no server-side sessions).

**API** (`/api/v1/auth`):

| Method | Path        | Auth              | Purpose                       |
|--------|-------------|-------------------|--------------------------------|
| POST   | `/register` | public            | Create an account              |
| POST   | `/login`    | public            | Exchange credentials for a JWT |
| GET    | `/me`       | `Bearer <token>`  | Fetch the caller's own profile |

Design patterns made explicit in `auth-service` (see in-code comments too):

| Pattern                  | Where |
|---------------------------|-------|
| Repository                | `UserRepository` isolates persistence from `AuthService` |
| DTO                       | `dto/*` — request/response shapes never leak `User` or the password hash |
| Strategy                  | `PasswordEncoder` interface / `BCryptPasswordEncoder` implementation |
| Chain of Responsibility   | `JwtAuthFilter` in the Spring Security filter chain |
| Layered architecture      | Controller → Service → Repository, each single-responsibility |
| Centralized error handling| `GlobalExceptionHandler` (`@ControllerAdvice`) maps domain exceptions to HTTP responses in one place |

## Running locally

From the repo root:

```bash
docker compose up --build
```

This starts `postgres` and `auth-service` on a shared network. The service
is reachable at `http://localhost:8080`. Postgres data persists across
restarts in the `postgres_data` volume; Flyway migrations re-apply
idempotently on every boot.

Try it end-to-end:

```bash
curl -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"secret123"}'
# -> 201, profile body, no password hash

curl -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"secret123"}'
# -> 200, {"accessToken": "...", "tokenType": "Bearer", ...}

curl localhost:8080/api/v1/auth/me \
  -H 'Authorization: Bearer <accessToken from above>'
# -> 200, profile matching the registered email, no password hash
```

## Running the tests

```bash
cd services/auth-service
mvn test
```

Unit tests cover the service-layer logic; the integration test spins up a
real PostgreSQL via Testcontainers and exercises the HTTP endpoints
end-to-end, so Docker must be running locally to execute it.

## Deferred work

The following services were split out of the original "microservices
ecommerce platform" scope and deliberately **not** built here — see
[`_bmad-output/implementation-artifacts/deferred-work.md`](_bmad-output/implementation-artifacts/deferred-work.md)
for details:

- API Gateway
- Product Catalog Service
- Cart Service
- Order Service
- Payment Service
- Inventory Service
- Notification Service

`auth-service` also does not implement OAuth/social login or refresh-token
rotation, and does not publish events to any message broker — none exists
yet in this repo.
