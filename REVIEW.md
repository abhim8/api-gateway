# Architecture Review — Critical Analysis of DESIGN.md v1

## Summary

The original design is **ambitious but over-engineered for V1**. It tries to build a complete platform gateway in one shot. Roughly 60% of the proposed code and infrastructure belongs in V2/V3. The design also reinvents several features that Spring Cloud Gateway and Spring Security provide out of the box.

---

## Per-Section Findings

### 1–2: Vision and Scope

| Issue | Severity | Recommendation |
|-------|----------|----------------|
| API key auth + PostgreSQL in V1 scope | High | Move to V2 — no PG dependency in V1 |
| Rate limiting (Redis token bucket) in V1 | High | Move to V2 — no Redis dependency in V1 |
| Dynamic routes + Redis route store | High | Move to V2 — V1 should be YAML-only |
| Admin endpoints (route CRUD) | High | Move to V2 — operational tooling, not core |

### 3: Functional Requirements

| Issue | Severity | Fix |
|-------|----------|-----|
| FR-03 (API key), FR-04 (rate limit), FR-15 (dynamic routes) are P0 | High | Downgrade to V2 |
| FR-02 (JWT) is correct | OK | Keep — but implement via Spring Security OAuth2 RS, not custom filter |
| FR-18/19 (request validation) | Low | Add in V1 — `spring.codec.max-in-memory-size` handles most of this |

### 4: Non-functional Requirements

| Issue | Severity | Fix |
|-------|----------|-----|
| NFR-10 (hot-reload < 5s) | Medium | V2 — no dynamic routes in V1 |
| NFR-03 (99.99%) | Medium | Aggressive for V1 — target 99.9% until proven in production |
| Too many NFRs for V1 | Medium | Trim to essentials |

### 5: High-level Architecture

| Issue | Severity | Fix |
|-------|----------|-----|
| Redis + PostgreSQL shown as shared infrastructure | High | Remove for V1 — gateway has zero external dependencies besides upstreams |
| OTel Collector shown in arch diagram | Medium | Keep — auto-instrumentation agent exports directly to collector |

### 6: Component Diagram

**Over-engineered components in V1:**
- `RateLimiter` (Redis) → V2
- `RouteDefinitionRepo` (Redis) → V2
- `AdminController` → V2

**Missing from diagram (but needed):**
- `Spring Security OAuth2 Resource Server` — replaces the custom JWT validator
- SCG's built-in filter factories — `RetryGatewayFilterFactory`, `CircuitBreakerGatewayFilterFactory`

### 7: Request Lifecycle

| Phase | Issue | Fix for V1 |
|-------|-------|------------|
| 4 (Auth) | Custom JWT + API key filters | Use Spring Security `oauth2ResourceServer().jwt()` |
| 5 (Rate limit) | Redis Lua script | Remove entirely for V1 |
| 6 (Route) | Complex custom matching | SCG `RouteLocator` handles this — no custom code |

### 8: Filter Pipeline

| Issue | Severity | Fix |
|-------|----------|-----|
| Custom `JwtAuthenticationFilter` | High | Remove — Spring Security OAuth2 RS handles this |
| Custom `ApiKeyAuthenticationFilter` | High | V2 |
| Custom `RateLimitingFilter` | High | V2 |
| Custom `MetricsFilter` as GlobalFilter | Medium | Remove — Micrometer auto-meters SCG requests |
| Custom `TracingFilter` as GlobalFilter | Medium | Remove — OTel Java agent auto-instruments |
| Custom `RequestLoggingFilter` | Low | Remove — SCG already logs; correlation ID + MDC covers this |
| Custom `CircuitBreakerFilterFactory` | High | Use SCG `CircuitBreakerGatewayFilterFactory` — config, not code |
| Custom `RetryFilterFactory` | Medium | Use SCG `RetryGatewayFilterFactory` — config, not code |
| Custom `TimeoutFilterFactory` | Medium | Use SCG route metadata `response-timeout` — config, not code |
| Custom `ResponseHeaderFilterFactory` | Low | Use SCG `RemoveResponseHeader` — config, not code |

### 9: Package Structure

**V1 should reduce ~30 packages to ~6:**
- ~~`route/dynamic/RedisRouteDefinitionRepository`~~ → V2
- ~~`ratelimit/`~~ → V2
- ~~`security/auth/ApiKeyFilterFactory`~~ → V2
- ~~`observability/MetricsFilterFactory`~~ → Remove (auto-config)
- ~~`observability/TracingFilterFactory`~~ → Remove (OTel agent)
- ~~`resilience/CircuitBreakerFilterFactory`~~ → Use SCG built-in
- ~~`admin/`~~ → V2
- ~~`model/GatewayProperties`~~ → Remove (use `spring.cloud.gateway.*` directly)

### 10: Routing Engine

**Over-engineered:**
- `RedisRouteDefinitionRepository` — V2
- `RouteRefreshListener` — V2
- Custom metadata for auth/rate-limit — V2

**Keep:** YAML-based route config with SCG built-in predicates. That's it.

### 11: Security Design

**This section has the biggest over-engineering problem.**

| What the original proposes | What V1 should do |
|---------------------------|-------------------|
| Custom `JwtAuthenticationFilterFactory` extending `AbstractGatewayFilterFactory` | `spring.security.oauth2.resourceserver.jwt.issuer-uri` — zero custom code |
| Custom `JwtTokenValidator` with manual JWKS fetching + caching | Spring Security's `NimbusReactiveJwtDecoder` handles this |
| Custom `SecurityContextExtractor` | `@AuthenticationPrincipal` or `exchange.getPrincipal()` |
| Custom API key auth flow | V2 |
| Role checking via `requiredRoles` in route metadata | Route-based authorization via `SecurityWebFilterChain` path matchers |

**JWKS caching** is built into Spring Security's `NimbusReactiveJwtDecoder` — no custom caching needed.

### 12: Rate Limiting

Remove entirely for V1. This is a full V2 feature requiring Redis.

### 13: Resilience

| Item | Verdict | V1 Approach |
|------|---------|-------------|
| Circuit breaker | Keep | `CircuitBreakerGatewayFilterFactory` in YAML — no custom code |
| Retry | Keep | `RetryGatewayFilterFactory` in YAML — no custom code |
| Timeout | Keep | Route metadata `response-timeout` in YAML — no custom code |
| Custom `FallbackController` | Keep | Minimal controller for CB fallback responses |
| Resilience4j `@Configuration` class | Remove | `application.yml` suffices for Resilience4j config |

### 14: Observability

| Item | Verdict | V1 Approach |
|------|---------|-------------|
| Structured JSON logging | Keep | `logback-spring.xml` with Logstash encoder |
| MDC context propagation | Keep | `Hooks.enableAutomaticContextPropagation()` + `CorrelationIdFilter` |
| Micrometer metrics | Keep | Auto-configured via `micrometer-registry-prometheus` |
| Custom metrics filters | Remove | SCG + Spring Security auto-publish metrics |
| OpenTelemetry tracing | Keep | OTel Java agent — zero code |
| Custom tracing spans | Remove | Agent auto-instruments HTTP calls; manual spans are V2 |
| Health endpoints | Keep | Auto-configured via actuator |
| Admin endpoints | Remove | V2 |

### 15: Configuration Model

| Issue | Fix for V1 |
|-------|------------|
| Redis connection config | Remove entirely |
| R2DBC/PostgreSQL config | Remove entirely |
| `GatewayProperties` with rate-limit, API key, etc. | Remove — use Spring Boot + SCG native config |
| Feature flags | Remove — over-engineering for V1 |

### 16: Deployment Architecture

| Issue | Fix for V1 |
|-------|------------|
| Separate admin port (9090) | Remove for V1 — not needed without admin endpoints |
| Custom metric for HPA (`gateway_requests_active`) | Change to standard `http_server_requests_seconds` (Micrometer auto) |
| Redis/PG in docker-compose | Remove — V1 has no external deps |

**Keep:** Jib build, K8s deployment, HPA, probes, graceful shutdown.

### 17: Folder Structure

**V1 folder structure should be dramatically simpler:**

```
api-gateway/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── src/
│   ├── main/java/com/example/gateway/
│   │   ├── GatewayApplication.java
│   │   ├── config/SecurityConfig.java
│   │   ├── filter/
│   │   │   └── CorrelationIdFilterFactory.java
│   │   ├── web/
│   │   │   └── FallbackController.java
│   │   └── common/
│   │       └── GlobalErrorHandler.java
│   └── resources/
│       ├── application.yml
│       └── logback-spring.xml
├── docker/Dockerfile
├── k8s/*.yaml
└── scripts/docker-compose.yml
```

### 18: Coding Standards

Keep — mostly solid. But remove the `JwtAuthenticationFilterFactory` template example since V1 doesn't need custom JWT filters.

### 19: Testing Strategy

| Issue | Fix for V1 |
|-------|------------|
| Testcontainers for Redis + PG | Remove — not needed without those deps |
| Performance tests with Gatling | V2 — verify perf after proving correctness |
| Contract tests with Spring Cloud Contract | V2 — unnecessary complexity for V1 |
| E2E tests in K8s | V2 |

**V1 testing approach:** JUnit 5 + Mockito + WireMock + WebTestClient. That's enough.

### 20: Development Roadmap

Needs complete rewrite. Original assumes a 10-week waterfall. Need incremental milestones.

---

## Summary of Changes

| Category | V1 Design (original) | V1 Design (revised) |
|----------|--------------------|--------------------|
| Custom classes | ~40 | ~6 |
| External deps (services) | Redis, PostgreSQL | None |
| Testcontainers | Redis, PostgreSQL | None |
| Test dependencies | Testcontainers, WireMock | WireMock only |
| Packages | 10 | 4 |
| Build file | Gradle | Gradle (migrate from Maven) |
| Custom JWT validation | Yes | Spring Security OAuth2 RS |
| Rate limiting | Yes | V2 |
| API key auth | Yes | V2 |
| Dynamic routes | Yes | V2 |
| Admin endpoints | Yes | V2 |
| Feature flags | Yes | V2 |
