---
title: 'Eligibility API — Financial Product Eligibility Microservice'
type: 'feature'
created: '2026-09-21'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '38fbc5c6714ba8ea048eca603af09c335be4b295'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The monorepo has no eligibility service, and there is no worked example of Redis caching, Resilience4j circuit-breaker/rate-limiter, async audit, Feign, Eureka, a load-balanced client, Actuator, and transactional dual-write in one Spring Boot service.

**Approach:** Add `services/eligibility-api` as a Java 21 / Spring Boot 3.3.4 module matching existing service layout (own POM under `spring-boot-starter-parent` 3.3.4 — this repo has no aggregator parent). Expose `GET /api/eligibility/{customerId}?productType=CREDIT_CARD` with those patterns wired in real code, plus README, Redis/Eureka compose snippet, and a gateway YAML scaffold.

## Boundaries & Constraints

**Always:**
- Match Boot 3.3.4 + Java 21 + `com.ecommerce.*` packaging; Spring Cloud BOM 2023.0.x.
- Own datastore (H2 local/tests so `mvn test` is offline). Redis cache of eligibility results keyed by `customerId`+`productType`, TTL 10 minutes.
- `@CircuitBreaker` on the credit-bureau call with fallback `"manual review required"`; force-failable mock bureau for demos.
- `@RateLimiter` ~50 req/s on the eligibility path; HTTP 429 via `@RestControllerAdvice`.
- `@Transactional` method persists the decision and updates customer eligibility history together.
- `@Async("auditExecutor")` audit log to DB via a dedicated `ThreadPoolTaskExecutor`.
- `@FeignClient` for `customer-profile-service`; `@LoadBalanced` RestTemplate/WebClient still configured for Eureka-name calls.
- Eureka client in `application.yml`; test profile disables Eureka so tests run offline.
- Actuator `/health`, `/metrics`, `/prometheus`; custom HealthIndicator covering Redis + circuit-breaker state.
- No secrets. Gateway is a docs/YAML scaffold only (full API Gateway remains deferred).

**Never:**
- Do not introduce a repo-wide parent POM/aggregator (existing services are independent).
- Do not build a real `customer-profile-service` or a running Spring Cloud Gateway module.
- Do not require Docker/Testcontainers/Redis/Eureka for `mvn test`.
- Do not put credentials or production secrets in source.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Eligible credit card | known customer, bureau up, passing score/KYC | 200, status `ELIGIBLE`, decision+history persisted, async audit row | N/A |
| Circuit breaker / bureau down | `force-fail` customer or mock force-fail flag | 200, status `MANUAL_REVIEW_REQUIRED`, reason mentions manual review required | Fallback; no 5xx |
| Rate limiter exceeded | more requests than limit in the refresh window | HTTP 429, JSON error body | `RequestNotPermitted` → handler |
| Missing productType | GET without query param | 400 | validation / missing param |
| Unknown productType | `productType=WIDGET` | 400 | type mismatch handler |

</frozen-after-approval>

## Code Map

- `services/auth-service/pom.xml` (and catalog/portal) -- Independent Boot 3.3.4 parent, Java 21, `com.ecommerce` group. **Reuse this module shape.** No root aggregator POM exists — do not create one.
- `services/auth-service/Dockerfile` -- Multi-stage Maven 3.9 + Temurin 21 JRE alpine. Copy this pattern.
- `docker-compose.yml` -- Shared `ecommerce-net`; add redis, optional Eureka, eligibility-api on host port 8083.
- `README.md` -- Service table + run instructions; add eligibility-api. Gateway remains deferred as a product; this work only scaffolds route YAML.
- `services/product-catalog-service/src/main/java/com/ecommerce/catalog/config/CacheConfig.java` -- `@EnableCaching` style; eligibility uses Redis instead of Caffeine.
- `services/*/exception/GlobalExceptionHandler.java` -- `@RestControllerAdvice` + `ErrorResponse` record. Reuse that error JSON shape.
- `services/*/model/*.java` -- No Lombok; JPA entities with UUID ids, explicit constructors, Flyway-owned schema (`ddl-auto: validate`).
- There is **no** `customer-profile-service` and **no** Eureka server in this repo. Feign by name + local/test stub; test profile `eureka.client.enabled=false`.
- `_bmad-output/implementation-artifacts/deferred-work.md` -- API Gateway is still deferred; do not claim a running gateway.

## Tasks & Acceptance

**Execution:**
- [x] `services/eligibility-api/pom.xml` -- Boot 3.3.4, Java 21, Cloud 2023.0.3 BOM; web, jpa, validation, cache, data-redis, aop, actuator, prometheus, h2, flyway, eureka-client, openfeign, loadbalancer, resilience4j-spring-boot3, starter-test
- [x] `services/eligibility-api/src/main/java/com/ecommerce/eligibility/EligibilityApiApplication.java` -- `@SpringBootApplication` + `@EnableFeignClients` + `@EnableDiscoveryClient`
- [x] Package layout `controller/`, `service/`, `client/`, `config/`, `model/`, `repository/`, `health/`, `exception/` (dto as needed)
- [x] Redis `@Cacheable` + `CacheConfig` TTL 10m, key customerId+productType
- [x] `@CircuitBreaker` credit-bureau client + fallback + force-failable mock
- [x] `@Async("auditExecutor")` audit entity + `ThreadPoolTaskExecutor` bean
- [x] `@RateLimiter` ~50/s + 429 handler
- [x] `@Transactional` decision + history dual-write
- [x] `@LoadBalanced` RestTemplate (or WebClient) bean; `@FeignClient` for customer-profile-service
- [x] `application.yml` Redis, R4j CB+RL, Eureka, bureau URL, datasource, actuator, cache TTL; test profile offline
- [x] Custom HealthIndicator for Redis + CB state; actuator health/metrics/prometheus
- [x] `docs/gateway-eligibility-route.yml` (and/or service docs) `/eligibility/**` → eligibility-api with Authorization + X-Request-Id propagation
- [x] docker-compose snippet Redis + mock Eureka; root compose entry for the service
- [x] `services/eligibility-api/README.md` run, curl, CB fallback demo, 429 demo
- [x] Tests: CB fallback → manual review required; rate limiter excess → 429; `mvn test` offline
- [x] Root `README.md` + `docker-compose.yml` updated

**Acceptance Criteria:**
- Given the test profile, when `mvn test` runs with no Redis/Eureka/Docker, then the build is green.
- Given a force-failed credit bureau, when eligibility is requested, then the body indicates manual review required.
- Given more requests than the rate-limiter window allows, when they hit the eligibility path, then the API returns 429.
- Given a successful evaluation, when the transactional persist runs, then both the decision row and the history row are written.

## Implementation Notes

Decisions recorded at planning (not user-visible product forks): package `com.ecommerce.eligibility`; host port 8083; GET left unauthenticated so the pattern demo does not depend on api-portal; default local/test `app.customer-profile.stub=true` because that service is not in the repo; H2 (PostgreSQL mode) rather than Testcontainers Postgres; rate-limiter test uses a tighter window via test properties so 429 is deterministic.

Feign `@FeignClient` beans are `@Primary` by default and must not implement `CustomerProfileClient`, or Spring reports multiple primary beans. Only `CustomerProfileGateway` implements that interface; the Feign client and stub are collaborators. `mvn test`: 11 tests, BUILD SUCCESS (offline).

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Route | Evidence |
|---|---|---|---|
| CacheConfig `@ConditionalOnBean(RedisConnectionFactory)` skips custom Redis manager | high | patch | User config is evaluated before Redis autoconfig; now `@ConditionalOnProperty(spring.cache.type=redis)`. |
| Health DEGRADED overwrites DOWN | medium | patch | Redis-down now takes precedence. |
| H2 console on by default | medium | patch | Now `${H2_CONSOLE_ENABLED:false}`. |
| CB test accepts any state | medium | patch | Asserts OPEN. |
| Decision rules / KYC / cache-hit untested | medium | patch | Added `EligibilityEngineTest`, low-score, unverified, cache-hit tests. |
| Gateway YAML: no RewritePath, bogus AddRequestHeader/sensitiveHeaders, duplicate copy | medium | patch | Fixed; both copies kept identical. |
| Service compose Eureka tag 4.1.4 | low | patch | Pinned to 4.1.1 like root compose. |
| No CacheErrorHandler (Redis outage = 500) | medium | defer | Beyond spec; adds surface. |
| customerId not validated (>64 chars = 500) | medium | defer | Needs validation + handler. |
| Async audit rejection, profile 5xx/null, first-check race, reason >512 chars | low/medium | defer | Real edge cases, not spec-required. |
| Unauthenticated service / demo toggle | n/a | reject | Spec states GET is unauthenticated for the pattern demo. |
| Port 8083 in spec vs 8086 in code | n/a | reject | Fix would edit this build's spec. |
| Cart/inventory/order README findings, compose name clash | false | reject | Not in this change / standalone-vs-root compose by design. |

## Design Notes

AOP annotations must cross bean boundaries: `EligibilityService` (`@RateLimiter`) → `EligibilityEngine` (`@Cacheable`) → `CreditBureauClient` (`@CircuitBreaker`); `AuditService` (`@Async("auditExecutor")`); `EligibilityPersistenceService` (`@Transactional`). Do not cache `MANUAL_REVIEW_REQUIRED` (stale fallback after bureau recovery). `timeoutDuration: 0s` on the rate limiter so excess calls fail immediately into 429.

## Verification

**Commands:**
- `cd services/eligibility-api && mvn test` -- expected: BUILD SUCCESS, including CB fallback and 429 tests
- `cd services/eligibility-api && mvn -q -DskipTests package` -- expected: jar built
