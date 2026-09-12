---
title: 'API Portal Service — Service-to-Service Client-Credentials Auth'
type: 'feature'
created: '2026-09-12'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/spec-product-catalog-service.md']
baseline_commit: 'f2dccf1257d8e16fc33dd707ecae0a900d68a2fb'
retroactive: true
implemented_on: 'origin/feat/api-portal-service (commits 4fb2acd, a90b2a5), stacked on feat/product-catalog-service'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `product-catalog-service`'s writes (`POST /categories`, `POST /products`) shipped fully open in v1 — no auth of any kind — and `auth-service` issues tokens only for human users. Once a second internal caller needed to write to the catalog, some form of service-to-service auth became necessary; `auth-service` was deliberately kept human-user-only rather than overloaded with a second responsibility.

**Approach:** A new `api-portal-service` implements OAuth2 client-credentials (RFC 6749 §4.4): microservices register with the portal (role `PRODUCER`/`CONSUMER`/`BOTH`, get a `clientId` + one-time `clientSecret`), a producer declares its protected APIs and required scopes, an operator grants a consumer access to a producer for a scope set (auto-approved, no workflow), and a consumer exchanges its credentials for a short-lived HS256 JWT via `POST /oauth/token`, scoped to exactly one producer audience. `product-catalog-service` is retrofitted to validate that JWT locally (shared secret, no network call back to the portal) on its two write endpoints.

**This is deliberately not the API Gateway** (still deferred): it issues/validates S2S tokens only — it does not route, proxy, aggregate, rate-limit, or front any external traffic.

**Note:** This spec is written retroactively against code already implemented on `origin/feat/api-portal-service` (not built via this workflow) so it can go through the same spec-anchored review as the rest of this repo before merging to `main`. It describes the system as built; known issues are carried forward to the Review Triage Log rather than silently fixed here.

## Boundaries & Constraints

**Always:**
- Every registered service has a unique `name` (slug) and unique `clientId`.
- Client secrets are BCrypt-hashed at rest; plaintext is returned exactly once (register, rotate-secret).
- Exactly one grant per (consumer, producer) pair (DB `UNIQUE` constraint + app-level pre-check) — 409 on duplicate.
- Every grant is auto-approved at creation — no pending/approval state exists.
- Issued token scopes are always the intersection of requested ∩ granted, never a superset.
- Tokens are short-lived (15 min default), access-token-only, HS256-signed.
- `product-catalog-service` GET endpoints stay public; only `POST /categories` and `POST /products` require `SCOPE_catalog:write`.
- Unknown `clientId` and wrong `clientSecret` return the identical generic 401 `INVALID_CLIENT` (no client enumeration).
- Portal and catalog derive the same HMAC key the same way (`Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`) from the same env var (`PORTAL_JWT_SECRET`), and the token's `aud` claim is checked with exact `Set.contains`, not substring match.

**Never:**
- No human approval workflow for grants.
- No refresh tokens, no server-side sessions (access-token-only, matches `auth-service`).
- No auth at all on the portal's own admin API (`/services`, `/apis`, `/grants`) — deliberately open in v1; there is no gateway yet to front an operator-only surface.
- `api-portal-service` never validates `auth-service` human-user JWTs, and vice versa — separate trust domains.
- `product-catalog-service` never calls `api-portal-service` over the network to validate a token — purely local signature/claims validation.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Register service success | unique kebab-slug `name`, role | 201, `ServiceCreatedResponse` incl. plaintext secret (once) | N/A |
| Register service duplicate name | name already taken (incl. concurrent race) | 409 `SERVICE_ALREADY_EXISTS` | JSON error body |
| Rotate secret | known service id | 200, new plaintext secret; old stops working immediately | N/A |
| Declare API, producer role | service has `PRODUCER`/`BOTH` role | 201, `ApiResponse` | N/A |
| Declare API, consumer-only role | service lacks producer role | 403 `NOT_A_PRODUCER` | JSON error body |
| Create grant success | consumer has `CONSUMER`/`BOTH`, producer has `PRODUCER`/`BOTH`, no existing grant | 201, auto-approved `GrantResponse` | N/A |
| Create grant, role mismatch | consumer/producer lacks the required role | 403 `NOT_A_CONSUMER` / `NOT_A_PRODUCER` | JSON error body |
| Create grant, duplicate | grant already exists for the pair | 409 `GRANT_ALREADY_EXISTS` | JSON error body |
| Issue token, valid credentials + grant | correct `clientId`/`clientSecret`, existing grant, valid audience | 200, JWT scoped to requested ∩ granted | N/A |
| Issue token, bad credentials | unknown `clientId` or wrong `clientSecret` | 401 `INVALID_CLIENT` (identical message either way) | N/A |
| Issue token, unknown audience | `audience` not an active registered producer | 400 `UNKNOWN_AUDIENCE` | JSON error body |
| Issue token, no grant | caller has no grant for the requested audience | 403 `GRANT_NOT_FOUND` | JSON error body |
| Issue token, ungranted scope | requested scopes ∩ granted scopes is empty | 403 `SCOPE_NOT_GRANTED` | JSON error body |
| Catalog write, valid token | `Bearer` token, `catalog:write` scope, correct audience | 201 (delegates to catalog's own create logic) | N/A |
| Catalog write, no/invalid token | missing, malformed, expired, or bad-signature token | 401 `UNAUTHORIZED` | N/A |
| Catalog write, wrong scope | valid token missing `catalog:write` | 403 `FORBIDDEN` | N/A |

</frozen-after-approval>

## Code Map

Already implemented — paths as they exist on `origin/feat/api-portal-service`.

**`services/api-portal-service/src/main/java/com/ecommerce/portal/`:**
- `ApiPortalServiceApplication.java` -- entry point; doc comment states S2S-only scope
- `controller/ServiceRegistryController.java` -- register/list/get/rotate-secret, declare/list APIs
- `controller/GrantController.java` -- create + filtered list
- `controller/TokenController.java` -- `POST /oauth/token`
- `dto/*.java` -- 10 request/response DTOs; only `ServiceCreatedResponse`/`RotateSecretResponse` carry plaintext secret
- `model/RegisteredService.java`, `ProducerApi.java`, `ConsumerGrant.java`, `ServiceRole.java` -- JPA entities + role enum
- `repository/*.java` -- Spring Data JPA repos (Repository pattern)
- `security/PortalJwtService.java` -- signs S2S tokens (`iss=api-portal`, `sub`=consumer, `aud`=producer, `scope`=space-delimited)
- `security/PasswordEncoderConfig.java` -- `PasswordEncoder` bean (Strategy pattern)
- `service/ServiceRegistryService.java`, `GrantService.java`, `TokenService.java` -- registration/rotation/declaration, grant creation, the client-credentials flow itself
- `util/Scopes.java` -- space-delimited-string ⇄ `Set`/`List` conversion
- `exception/*.java` -- 9 domain exceptions + `ErrorResponse` + `GlobalExceptionHandler`, one exception per failure mode
- `resources/application.yml`, `resources/db/migration/V1-V3__*.sql` -- config + Flyway-only schema
- `Dockerfile`, `pom.xml` -- no `spring-boot-starter-security` (only `spring-security-crypto`) since the admin API is intentionally open

**`product-catalog-service` retrofit (changed/added on this branch):**
- `security/PortalAuthFilter.java` (new) -- Chain-of-Responsibility filter, resolves JWT → `Authentication` with `SCOPE_*` authorities
- `security/PortalJwtValidator.java` (new) -- HS256 verify + audience check
- `security/SecurityConfig.java` (new) -- filter chain: `permitAll` on GET, `hasAuthority("SCOPE_catalog:write")` on the two write POSTs
- `resources/application.yml` -- adds `app.portal.jwt.secret`/`audience`
- `pom.xml` -- adds `spring-boot-starter-security`, `jjwt-*`, `spring-security-test`

## Tasks & Acceptance

**Execution (all already implemented on `feat/api-portal-service`, commits `4fb2acd` + `a90b2a5`):**
- [x] `services/api-portal-service/pom.xml` -- project scaffold
- [x] `.../model/RegisteredService.java`, `ProducerApi.java`, `ConsumerGrant.java`, `ServiceRole.java` -- domain model
- [x] `.../repository/*.java` -- Repository pattern
- [x] `.../db/migration/V1-V3__*.sql` -- Flyway schema
- [x] `.../dto/*.java` -- request/response DTOs
- [x] `.../security/PortalJwtService.java`, `PasswordEncoderConfig.java` -- signing + hashing
- [x] `.../service/ServiceRegistryService.java`, `GrantService.java`, `TokenService.java` -- business logic
- [x] `.../controller/*.java` -- API layer
- [x] `.../exception/*.java` -- centralized error handling
- [x] `.../resources/application.yml` -- config
- [x] `services/api-portal-service/Dockerfile`, `docker-compose.yml` entry -- containerization
- [x] `.../test/.../GrantServiceTest.java`, `ServiceRegistryServiceTest.java`, `TokenServiceTest.java` -- unit tests
- [x] `.../test/.../PortalControllerIntegrationTest.java` -- integration tests
- [x] `services/product-catalog-service/.../security/PortalAuthFilter.java`, `PortalJwtValidator.java`, `SecurityConfig.java` -- retrofit
- [x] `services/product-catalog-service/.../test/.../CatalogControllerIntegrationTest.java` -- retrofitted with real minted-token tests

**Acceptance Criteria:**
- Given a service registers as `CONSUMER` and is granted access to `product-catalog-service` for `catalog:write`, when it requests a token with that scope, then the token validates against catalog's own filter chain and a subsequent `POST /categories` with it succeeds.
- Given a token has no `catalog:write` scope, when it's presented to `POST /products`, then the request is rejected with 403, not 401.
- Given two services independently configured with the *same* `PORTAL_JWT_SECRET` value (as `docker-compose.yml` wires by default), when a token from one is presented to the other, then validation succeeds without any network call between them.

## Design Notes

Patterns made explicit:
- **Repository**, **DTO**, **Layered architecture**, **Centralized error handling** — same conventions as `auth-service`/`product-catalog-service`.
- **Strategy** — `PasswordEncoder`/`BCryptPasswordEncoder`, identical shape to `auth-service`'s.
- **Chain of Responsibility** — only in the catalog-service retrofit (`PortalAuthFilter` in the filter chain), same role as `auth-service`'s `JwtAuthFilter`. The portal service itself has no filter chain — its own admin API is unauthenticated by design.
- **Single-use-token pattern** (implicit) — `ServiceCreatedResponse`/`RotateSecretResponse` surface a plaintext secret exactly once, never persisted or re-returned.

## Verification

**Commands:**
- `cd services/api-portal-service && mvn test` -- expected: all unit + integration tests pass
- `cd services/product-catalog-service && mvn test` -- expected: retrofitted tests (real minted-token round trips) pass
- `docker compose up --build` -- expected: all four containers (two Postgres + two services) healthy, and a token minted from `api-portal-service` is accepted by `product-catalog-service`'s live filter chain

## Implementation Notes

Code already exists on `origin/feat/api-portal-service`. This spec was authored retroactively (2026-09-12) from a full-code investigation (not just the diff) to anchor a structured review before merge. The JWT-sharing mechanism was traced end-to-end and is genuinely wired correctly (same library/algorithm/key-derivation/claim shape, matching default config) — not merely superficially present. Known gaps surfaced during investigation — carried to review rather than fixed here:
- **Declared APIs are never enforced.** `TokenService` never consults `ProducerApiRepository`, and `GrantService.createGrant` never cross-checks requested scopes against a producer's declared `requiredScopes`. The "declare API" step is metadata only, not a real authorization input.
- **No uniqueness constraint on `producer_apis` (method, pathPattern)** — the same API can be declared multiple times.
- **Secret-sharing between the two services is convention-only, not code-enforced.** Nothing detects or warns on a `PORTAL_JWT_SECRET` drift between the two services' configs; a mismatch degrades to opaque 401s with no diagnostic pointing at the cause.
- **No automated test proves the two live services actually interoperate.** The catalog-service integration test mints tokens using its own injected config, not by calling a real running `api-portal-service` — cross-service compatibility is asserted by code inspection, not by any test.
- **Expired-token rejection is unverified by any test**, in either service, though the code path (jjwt validates `exp` automatically) exists.
- **No self-grant / self-audience guard** — a `BOTH`-role service can grant itself access to itself; not addressed anywhere in code or docs.
- **Portal admin API's `rotate-secret` endpoint requires no auth and no ownership proof** — any caller who can reach the portal's admin surface can invalidate any registered service's credential and mint a fresh one for it. Consistent with the stated "v1 admin API is intentionally open" cut, but its blast radius (credential takeover) is broader than plain registration.
- No test exercises any of the portal's own `400 VALIDATION_ERROR` paths, malformed-JSON handling.

**Post-review patch round (2026-09-12):** all 7 `patch`-routed findings fixed directly (no live implementation agent for this externally-built branch):
1. **High — `ServiceRegistryService.register`'s race guard was dead code.** `RegisteredService` now implements `Persistable<UUID>`; `register()` uses `saveAndFlush(...)`. 3rd occurrence of the identical bug found and fixed this session (`auth-service`, `product-catalog-service`, now here). Added `ConcurrentRegistrationTest` — empirically confirmed broken before (0 caught), fixed after (7/7 caught).
2. **High — `GrantService.createGrant` had no race protection at all.** Added the missing `try/catch(DataIntegrityViolationException)` → `DuplicateGrantException`; `ConsumerGrant` now implements `Persistable<UUID>`; `createGrant` uses `saveAndFlush(...)`. Added `ConcurrentGrantCreationTest` — same empirical confirmation (0 → 7/7 caught).
3. **Medium — non-UUID path/query values fell to 500.** Added `MethodArgumentTypeMismatchException` handler to the portal's `GlobalExceptionHandler`.
4. **Medium — scope strings weren't trimmed.** `TokenService` now trims and filters blank entries from requested scopes before set-intersection.
5. **Low — missing 404/409 integration coverage.** Added `listApis_onUnknownServiceId_returns404` and a real-HTTP duplicate-grant test to `PortalControllerIntegrationTest`.
6. **Low — missing tampered-signature and missing-scope-on-`/products` tests.** Added both to `CatalogControllerIntegrationTest`.
7. **Low — dead `.toLowerCase()`** in `ServiceRegistryService.register()` removed (bean validation already rejects uppercase input).

Full suites after patches: `product-catalog-service` 30/30, `api-portal-service` 31/31.

## Spec Change Log

## Review Triage Log

Reviewed 2026-09-12 via 4 layers (Blind Hunter, Edge Case Hunter, Verification Gap, Acceptance Auditor) against `feat/api-portal-service` merged onto the patched `main`.

- **[high → patch]** `ServiceRegistryService.register`'s existing `try/catch(DataIntegrityViolationException)` is dead code — 3rd occurrence of the exact bug already found and fixed twice (`auth-service`, `product-catalog-service`): `RegisteredService.id` is a client-assigned UUID with no `@GeneratedValue`/`Persistable`, so `save()` defers its INSERT to commit time, after the catch has already returned. Verified by code inspection matching the identical, now-proven pattern. Fix: `RegisteredService implements Persistable<UUID>` + `saveAndFlush(...)`. (verification-gap)
- **[high → patch]** `GrantService.createGrant` has **no** race protection at all — not even a (broken) try/catch — despite the V3 migration's own comment stating re-granting a duplicate pair "is rejected (409) rather than silently overwriting." `ConsumerGrant` has the same unprotected client-assigned-UUID shape. Fix: same `Persistable<UUID>` + `saveAndFlush(...)` + add the missing try/catch mapping to `DuplicateGrantException`. (verification-gap + acceptance-auditor + edge-case-hunter, one entry)
- **[medium → patch]** No `MethodArgumentTypeMismatchException` handler in the portal's `GlobalExceptionHandler` — non-UUID path/query params on `GrantController`/`ServiceRegistryController` fall to the catch-all → 500 instead of 400. Same fix as catalog's identical finding. (edge-case-hunter)
- **[medium → patch]** `TokenService` doesn't trim scope strings before set-intersection — incidental whitespace in a requested scope silently fails to match a granted scope, wrongly returning 403 `SCOPE_NOT_GRANTED` instead of matching. (edge-case-hunter)
- **[low → patch]** No integration test covers declare-API on an unknown service id (should 404), and the duplicate-grant 409 path is only unit-tested against a mock, never through a real HTTP+DB round trip (unlike the analogous duplicate-service-name case, which is). (blind-hunter)
- **[low → patch]** `CatalogControllerIntegrationTest` never asserts a tampered-signature token is rejected (only "no token," "wrong scope," "wrong audience") — a genuinely distinct code path (signature verification fails before audience is even checked). (blind-hunter)
- **[low → patch]** AC2's literal scenario ("token missing `catalog:write` presented to `POST /products`") has no direct test — only the `/categories` variant exists. `SecurityConfig` applies the same check to both, so this is very likely already correct, but adding the mirror test is cheap and closes the literal AC. (acceptance-auditor)
- **[low → patch]** Dead `.toLowerCase()` call in `ServiceRegistryService.register()` — `ServiceRegisterRequest.name`'s `@Pattern` already rejects any uppercase input via bean validation, so the call can never change anything and misleadingly suggests case-insensitive registration. Trivial removal, bundled with the Persistable fix to the same file. (blind-hunter)
- **[false]** Declared producer APIs (`POST /services/{id}/apis`) are never enforced by `TokenService` — described as an undiscovered gap. Refuted: already documented verbatim in this spec's own Implementation Notes ("Declared APIs are never enforced... metadata only, not a real authorization input"). (blind-hunter + edge-case-hunter, one entry)
- **[false]** No uniqueness constraint on `producer_apis`, allowing duplicate declarations. Refuted: already documented in this spec's Implementation Notes ("No uniqueness constraint on `producer_apis`... the same API can be declared multiple times"). (edge-case-hunter)
- **[false]** AC1's cross-service interoperability (a token minted by a live `api-portal-service` accepted by a live `product-catalog-service`) isn't tested end-to-end. Refuted by the auditor's own admission: already documented verbatim in this spec's Implementation Notes as a pre-acknowledged, carried-forward limitation, not a new discovery. (acceptance-auditor)
- **[false]** `PortalJwtService`'s `app.portal.jwt.secret` property was claimed to also control token expiry. Refuted: verified in code — `expiration-ms` and `secret` are separate constructor parameters/config keys; the class's own javadoc correctly describes both as independently config-driven. (edge-case-hunter)
- **[low → rejected]** `PortalJwtValidator` never checks the `iss` claim. Real, but only `api-portal-service` ever holds the signing secret in this system — a token with a forged `iss` still can't be produced without that secret, so `aud`+signature already gate everything an `iss` check would add. Defense-in-depth for a future multi-issuer scenario that doesn't exist yet; rejected as low value now. (blind-hunter)
- **[low → rejected]** `INVALID_CLIENT`'s "no enumeration" guarantee has a narrow bypass: the role check fires only after a correct secret match, so a correct-secret-wrong-role request gets a distinguishable 403 instead of 401. Real, but only reachable by someone who already has the correct 256-bit-entropy secret — the oracle can't help find it, since you need it to reach the branch that exposes the oracle. Rejected as low-impact given the secret's entropy makes any guessing attack infeasible regardless. (acceptance-auditor)
- **[low → rejected]** Self-grant (`consumerServiceId == producerServiceId` on a `BOTH`-role service) isn't prevented. Not clearly harmful either way (no incorrect behavior results, just an unusual grant), and the fix adds a new guard/exception — rejected per the low+guard-fix rule rather than escalated as a full decision-needed checkpoint for something this minor. (edge-case-hunter)
- **[low → rejected]** `DataIntegrityViolationException` in `ServiceRegistryService.register` is assumed to always be the `name` constraint; a `client_id` collision would be misreported. `client_id` includes 96 bits of random suffix — collision is not practically reachable. Rejected. (edge-case-hunter)
- **[low → rejected]** No pagination on `GET /services`/`GET /grants`, inconsistent with catalog's paginated product listing. Real inconsistency, low severity at this project's expected scale (dozens of registered services, not thousands); fix is more than trivial (new query params + response shape). Rejected. (blind-hunter)
- **[low → rejected]** N+1 queries in `GrantService` resolving consumer/producer display names per grant instead of batched. Real, but a performance nitpick at demo scale, not a correctness bug; fix is more than a direct correction. Rejected. (blind-hunter)
- **[low → rejected]** No dedicated unit test for the `Scopes` utility. Already exercised indirectly via `TokenServiceTest`/`GrantServiceTest`; low incremental value. Rejected. (blind-hunter)
- **[low → rejected]** `TokenRequest.scope` accepts a JSON array rather than the RFC 6749 space-delimited string used internally. Not a defect — a JSON REST API accepting an idiomatic array input while producing an RFC-compliant claim internally is a defensible design choice, not a violation. Rejected as false-premise/non-issue. (blind-hunter)
- **[low → rejected]** `TokenRequest.scope` list entries have no per-element `@NotBlank`, unlike `GrantCreateRequest.scopes`. Real minor DTO inconsistency; low impact, fix adds a validation guard. Rejected per the low+guard-fix rule. (acceptance-auditor)
- **[defer]** JWT secret rotation has no `kid`/dual-key support for a graceful rotation window — a real operational gap, but an architectural enhancement beyond a patch-sized fix. (blind-hunter)
