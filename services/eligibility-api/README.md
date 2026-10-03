# Eligibility API — financial product eligibility

Spring Boot 3.3.4 / Java 21 microservice that answers:

```
GET /api/eligibility/{customerId}?productType=CREDIT_CARD
```

It is a worked example of enterprise Spring annotations, all actually wired
(not empty stubs): `@Cacheable` (Redis, 10 min TTL), `@CircuitBreaker`,
`@Async("auditExecutor")`, `@RateLimiter` (~50 req/s → HTTP 429),
`@Transactional` dual-write, `@LoadBalanced` RestTemplate, `@FeignClient`,
Eureka registration, and Actuator (`/health`, `/metrics`, `/prometheus`)
with a custom Redis + circuit-breaker `HealthIndicator`.

`customer-profile-service` is not in this monorepo. Local/test traffic uses
an in-process stub (`app.customer-profile.stub=true`). Point Feign/Eureka
at a real instance by setting `CUSTOMER_PROFILE_STUB=false` and running
Eureka.

## Run

### Offline (no Redis, no Eureka)

```bash
cd services/eligibility-api
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### With Redis + Eureka

```bash
# from this directory
docker compose up -d
mvn spring-boot:run
```

Or from the repo root, with the other ecommerce services:

```bash
docker compose up --build
```

Host port is **8086** (container 8080).

## Curl

```bash
# Eligible credit card (stub profile + mock bureau score 720 / 790 for high-score-*)
curl -s "http://localhost:8086/api/eligibility/high-score-ada?productType=CREDIT_CARD" | jq

# Ineligible (low mock score)
curl -s "http://localhost:8086/api/eligibility/low-score-ada?productType=CREDIT_CARD" | jq

# Actuator
curl -s http://localhost:8086/actuator/health | jq
curl -s http://localhost:8086/actuator/prometheus | head
```

Product types: `CREDIT_CARD`, `PERSONAL_LOAN`, `MORTGAGE`.

## Demo: circuit-breaker fallback

The mock bureau throws when `customerId` starts with `force-fail`, or when
the demo toggle is on. Resilience4j `@CircuitBreaker` then returns a
fallback the engine maps to **manual review required** (HTTP 200, not 5xx).

```bash
# One-shot fallback via customer id
curl -s "http://localhost:8086/api/eligibility/force-fail-demo?productType=CREDIT_CARD" | jq
# -> status: MANUAL_REVIEW_REQUIRED, reason contains "manual review required"

# Or flip the mock for every subsequent customer
curl -s -X POST "http://localhost:8086/api/eligibility/demo/credit-bureau/fail?enabled=true" | jq
curl -s "http://localhost:8086/api/eligibility/anyone?productType=CREDIT_CARD" | jq
curl -s -X POST "http://localhost:8086/api/eligibility/demo/credit-bureau/fail?enabled=false" | jq
```

Manual-review results are **not** cached, so turning the bureau back on is
visible immediately.

## Demo: HTTP 429 rate limit

The limiter is 50 requests per second (`timeoutDuration: 0s` → fail
immediately). Burst past it:

```bash
for i in $(seq 1 80); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    "http://localhost:8086/api/eligibility/burst-$i?productType=CREDIT_CARD" &
done
wait
# expect a mix of 200 and 429
```

429 body: `{"code":"RATE_LIMIT_EXCEEDED","message":"Too many eligibility requests; retry later",...}`.

## Tests

```bash
cd services/eligibility-api
mvn test
```

The `test` profile disables Eureka, excludes Redis, uses in-memory H2 and
the customer-profile stub. No Docker required.

## Config (env)

| Variable | Default | Purpose |
|----------|---------|---------|
| `REDIS_HOST` / `REDIS_PORT` | localhost:6379 | Cache |
| `EUREKA_URI` | http://localhost:8761/eureka | Registry |
| `EUREKA_ENABLED` | true (false on `test`/`local`) | Register/discover |
| `CREDIT_BUREAU_MODE` | `mock` | `mock` or `http` |
| `CREDIT_BUREAU_URL` | http://localhost:9090 | Used when mode=http |
| `CREDIT_BUREAU_FORCE_FAIL` | false | Open the fallback path |
| `CUSTOMER_PROFILE_STUB` | true | In-process profile |
| `CUSTOMER_PROFILE_CLIENT` | feign | `feign` or `rest` (`@LoadBalanced`) |

Gateway route scaffold (not a running gateway): [`docs/gateway-eligibility-route.yml`](../../docs/gateway-eligibility-route.yml).
