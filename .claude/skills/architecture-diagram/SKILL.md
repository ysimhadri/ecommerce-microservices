---
name: architecture-diagram
description: 'Generate a high-level architecture diagram (Mermaid) of this monorepo from docker-compose.yml and the services/ directory, and save it to docs/architecture.md. Use when the user asks for an architecture diagram, system overview, or "how do the services fit together".'
---

# Architecture Diagram

Produce a current, evidence-based high-level diagram. Never draw an edge you did not find in code or config.

## Steps

1. Enumerate services: `ls services/` and the service blocks in `docker-compose.yml` (image, host port, `depends_on`, `*_URL` / `*_URI` env vars, dedicated databases, Redis, Eureka).
2. Find service-to-service calls: grep each `services/*/src/main` for `*_URL`, `@FeignClient`, `RestTemplate`/`RestClient`/`WebClient` targets, and token/scope usage (e.g. `catalog:write`). Each `*_SERVICE_URL` env var in compose is an edge from that service to the target.
3. Note the deferred/not-built components from `_bmad-output/implementation-artifacts/deferred-work.md` (Payment, Notification, API Gateway) and draw them dashed as "planned"; a gateway that exists only as a YAML scaffold is also dashed.
4. Write `docs/architecture.md` containing:
   - One Mermaid `flowchart LR` with: clients, each service (label with host port), its datastore, shared infra (Redis, Eureka), and labelled edges (sync HTTP, S2S token, saga step, cache, discovery).
   - A short legend and a "Notes" list of anything uncertain.
5. Show the diagram in the reply and link the file. Do not commit or push unless asked.

## Rules

- Group by concern with `subgraph` (Identity, Catalog, Order flow, Eligibility, Infra).
- Label edges with the protocol/purpose, not just arrows.
- Re-verify ports against `docker-compose.yml` every run; do not reuse an old diagram from memory.
