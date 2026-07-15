# API Gateway — Software Design Document

> **Project**: API Gateway — front-door proxy for a microservices platform
> **Version**: 1.0
> **Java**: 26
> **Runtime**: Spring Cloud Gateway 5.x / Spring Boot 4.x / Netty / WebFlux / Reactor

---

## 1. Vision and Goals

### Vision

Build a lightweight, secure, and observable API Gateway that acts as the single entry point for external traffic into the platform's microservices ecosystem — using **configuration over custom code** wherever possible.

### V1 Goals

| Goal | How |
|------|-----|
| **Security-first** | Every request goes through an `AuthenticationProvider`; never bypassed |
| **Resilient by default** | Circuit breaker, retry, and timeout via SCG built-in filter factories |
| **Observable** | Structured JSON logs, Micrometer metrics, OpenTelemetry traces |
| **Operable** | Health probes, graceful shutdown, Docker ready |
| **Maintainable** | Minimal custom code (~11 classes); everything else is YAML config |

### Architecture Tenets

1. **Stateless**: Zero local state. No Redis, no database. Every pod is identical.
2. **Configuration over code**: SCG built-in filters handle 80% of requirements. Custom code is the exception.
3. **Reactive end-to-end**: Netty event loop does all work — no thread pool blocking.
4. **Fail-fast**: Expired or malformed requests are rejected immediately — no upstream round-trip.
5. **Defend upstreams**: Circuit breakers, retries, and timeouts protect backend services from cascading failure.

---

## 2. Architecture

```
                    ┌─────────────┐
                    │   Clients   │
                    └──────┬──────┘
                           │ HTTPS
                           ▼
                  ┌─────────────────┐
                  │   K8s Ingress   │
                  │ (TLS termination)│
                  └────────┬────────┘
                           │ HTTP
                           ▼
            ┌─────────────────────────────────┐
            │     API Gateway Pod (xN)        │
            │  Spring Cloud Gateway 5.x       │
            │  Netty / WebFlux / Reactor      │
            │  Java 26                        │
            │                                 │
             │  ┌─── Filter Pipeline ───────┐ │
             │  │ CORS → Correlation →      │ │
             │  │ Auth → Route Match →      │ │
             │  │ Resilience → Proxy → Resp │ │
             │  └───────────────────────────┘ │
            └────┬────────┬────────┬─────────┘
                 │        │        │
           ┌─────┘        │        └─────┐
           ▼              ▼              ▼
     ┌──────────┐  ┌──────────┐  ┌──────────┐
     │ Service-A│  │ Service-B│  │ Service-C│
     │ Upstream │  │ Upstream │  │ Upstream │
     └──────────┘  └──────────┘  └──────────┘

     ┌─────────────────────────────────────┐
     │         Platform Infrastructure     │
     │  Prometheus + Grafana (metrics)     │
     │  OpenTelemetry Collector (traces)   │
     │  Loki / Datadog (logs)              │
     └─────────────────────────────────────┘
```

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| No Spring Security | Auth is handled entirely by `AuthenticationGlobalFilter` + `AuthenticationProvider`. Avoids OAuth2/JWKS complexity for the current architecture. |
| Strategy pattern for auth | `MockAuthenticationProvider` vs `RemoteAuthenticationProvider` selected via `gateway.auth.mode` property. Only one active at a time. |
| Reactive end-to-end | WebFlux + Netty for zero blocking. No thread pool for request handling. |
| Configuration over code | SCG built-in filters for retry, circuit breaker, timeout, header manipulation. Custom code only where SCG has no built-in equivalent. |

---

## 3. Request Lifecycle

```
Client                  Gateway                           Upstream
  │                       │                                  │
  │──── GET /api/v1/users ──►                                  │
  │                       │                                  │
  │                   ┌───▼──────────┐                       │
  │                   │ 1. Netty     │                       │
  │                   │ parse headers│                       │
  │                   └───┬──────────┘                       │
  │                       │                                  │
  │                   ┌───▼──────────┐                       │
  │                   │ 2. CORS      │                       │
  │                   │ check origin │                       │
  │                   │ preflight?   │                       │
  │                   └───┬──────────┘                       │
  │                       │                                  │
  │                   ┌───▼──────────┐                       │
  │                   │ 3. Correlation│                      │
  │                   │ ID filter    │                       │
  │                   │ gen/forward  │                       │
  │                   └───┬──────────┘                       │
  │                       │                                  │
  │                   ┌───▼──────────┐                       │
  │                   │ 4. Auth      │                       │
  │                   │ GlobalFilter  │                       │
  │                   │ ─ Authenticate│                      │
  │                   │ ─ If fail:401 │                      │
  │                   └───┬──────────┘                       │
  │                       │                                  │
  │                   ┌───▼──────────┐                       │
  │                   │ 5. Route     │                       │
  │                   │ matcher      │                       │
  │                   │ (predicates) │                       │
  │                   └───┬──────────┘                       │
  │                       │                                  │
  │                   ┌───▼──────────┐                       │
  │                   │ 6. Retry     │                       │
  │                   │ filter       │                       │
  │                   └───┬──────────┘                       │
  │                       │                                  │
  │                   ┌───▼──────────┐                       │
  │                   │ 7. Circuit   │                       │
  │                   │ Breaker      │                       │
  │                   │ (state check)│                       │
  │                   └───┬──────────┘                       │
  │                       │                                  │
  │                   ┌───▼──────────┐                       │
  │                   │ 8. Proxy     │──────────────────────►│
  │                   │ (timeout)    │                       │
  │                   │              │◄──────────────────────│
  │                   │ Response     │                       │
  │                   └───┬──────────┘                       │
  │                       │                                  │
  │                   ┌───▼──────────┐                       │
  │                   │ 9. Post-filters│                     │
  │                   │ strip headers │                      │
  │                   │ log complete  │                      │
  │                   └───┬──────────┘                       │
  │                       │                                  │
  │◄──── HTTP Response ────                                  │
```

| Phase | Mechanism | Error Response |
|-------|-----------|----------------|
| 1 | Netty HTTP parser | 400 (malformed) |
| 2 | `CorsGlobalFilter` | 403 (origin denied) |
| 3 | `CorrelationIdGlobalFilter` | — |
| 4 | `AuthenticationGlobalFilter` → `AuthenticationProvider` | 401 (unauthenticated), 500 (provider error) |
| 5 | SCG `RouteLocator` | 404 (no matching route) |
| 6 | SCG `RetryGatewayFilterFactory` | 502 if all retries fail |
| 7 | SCG `CircuitBreakerGatewayFilterFactory` | 503 + fallback |
| 8 | Netty `HttpClient` with `response-timeout` | 504 (upstream timeout) |
| 9 | SCG post-filters + `GlobalErrorHandler` | — |

---

## 4. Filter Pipeline

### Filter Order

```
Order   Filter                          Source        Type
────────────────────────────────────────────────────────────
-200    CorsGlobalFilter                SCG built-in  Global
-100    CorrelationIdGlobalFilter       Custom        Global
 -75    AuthenticationGlobalFilter       Custom        Global
  +0    Route predicates + per-route    SCG + YAML    Per-route
        filters (Retry, CB, headers)
 +100   GlobalErrorHandler               Custom        Error handler
```

### Global Filters

| Filter | Implementation | Notes |
|--------|---------------|-------|
| CORS | `spring.cloud.gateway.globalcors` | YAML config only |
| Correlation ID | `CorrelationIdGlobalFilter` | Custom `GlobalFilter` |
| Authentication | `AuthenticationGlobalFilter` | Always runs; delegates to `AuthenticationProvider` |
| Error handling | `GlobalErrorHandler` | Custom `ErrorWebExceptionHandler` |

### Per-Route Filters (YAML config)

| Filter | SCG Factory |
|--------|-------------|
| Retry | `RetryGatewayFilterFactory` |
| Circuit Breaker | `CircuitBreakerGatewayFilterFactory` |
| Add header | `AddRequestHeaderGatewayFilterFactory` |
| Remove header | `RemoveRequestHeaderGatewayFilterFactory` |
| Rewrite path | `RewritePathGatewayFilterFactory` |
| Strip prefix | `StripPrefixGatewayFilterFactory` |

---

## 5. Authentication Delegation

Every request goes through `AuthenticationGlobalFilter` (order -75) before route matching. The filter delegates to a configurable `AuthenticationProvider`:

```
Request → AuthenticationGlobalFilter → AuthenticationProvider → Result
                                      ┌─────────────────────┐
                                      │ Mock (mode=mock)    │
                                      │  → authenticated    │
                                      ├─────────────────────┤
                                      │ Remote (mode=remote)│
                                      │  → throws           │
                                      │    UnsupportedOp    │
                                      └─────────────────────┘
                                            │
                                            ▼
                                      AuthenticationResult
                                      ├ authenticated=false → 401
                                      └ authenticated=true → continue
```

### Mode Selection

```yaml
gateway:
  auth:
    mode: mock    # or "remote"
```

### AuthenticationProvider interface

```java
Mono<AuthenticationResult> authenticate(ServerWebExchange exchange);
```

Returns `AuthenticationResult` with:
- `authenticated` — boolean
- `subject` — principal identifier
- `roles` — list of role strings (future use)
- `permissions` — list of permission strings (future use)
- `claims` — map of arbitrary claims (future use)

### MockAuthenticationProvider

- Always returns `authenticated = true` with subject `"mock-user"`
- No HTTP calls, no JWT, no crypto — zero I/O
- Used for local development and CI

### RemoteAuthenticationProvider

- Currently throws `UnsupportedOperationException("Auth Platform integration is not implemented yet.")`
- Will later make an HTTP call to the Auth Platform's `/auth/validate` endpoint
- **Note:** Gateway code touches nothing in the `auth-platform` project; it only prepares for future integration

---

## 6. Routing

Routes are defined entirely in YAML using SCG's declarative `RouteLocator`. No custom routing code.

### Route Resolution

```
1. Netty reads HTTP headers
2. CORS validation (globalcors)
3. Correlation ID filter adds X-Correlation-ID
4. AuthenticationGlobalFilter runs AuthenticationProvider
5. SCG RouteLocator evaluates each route's predicates in order
6. First matching route: combine global + route filters
7. No match → 404
```

### Per-Route Configuration

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: example
          uri: http://example-service:8080
          predicates:
            - Path=/api/v1/example/**
          filters:
            - StripPrefix=1
            - name: Retry
              args:
                retries: 3
                series: SERVER_ERROR
                methods: GET
            - name: CircuitBreaker
              args:
                name: exampleCB
                fallbackUri: forward:/fallback/example
          metadata:
            response-timeout: 5000
```

### Timeout

| Scope | Configuration |
|-------|--------------|
| Per-route | Route metadata `response-timeout` (ms) |
| Global default | `spring.cloud.gateway.httpclient.response-timeout` |

---

## 7. Resilience

### Circuit Breaker

SCG's `CircuitBreakerGatewayFilterFactory` backed by Resilience4j. Opens on configurable failure threshold, routes to `FallbackController` when open.

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
```

### Retry

SCG's `RetryGatewayFilterFactory`. Retries GET requests only (idempotent) on server errors (5xx).

### Timeout

- Per-route: `metadata.response-timeout` (ms)
- Global: `spring.cloud.gateway.httpclient.response-timeout`

### Fallback Controller

When a circuit breaker opens, requests are forwarded to `FallbackController` which returns a structured 503 response with correlation ID, route name, and timestamp.

### Summary

| Pattern | Trigger | Response |
|---------|---------|----------|
| Circuit Breaker | Failure threshold exceeded | 503 + fallback |
| Retry | 5xx response | Transparent (max 3, GET only) |
| Timeout | No response within window | 504 |

---

## 8. Observability

### Structured Logging

Log4j2 with `JsonTemplateLayout` outputs JSON to stdout. Fields: `timestamp`, `level`, `logger`, `message`, `thread`, `method`, `line`, `correlationId`, `traceId`, `spanId`, `exception`.

MDC context is populated by `CorrelationIdGlobalFilter` and bridged across threads via `Hooks.enableAutomaticContextPropagation()`.

### Metrics

Micrometer auto-configures with `micrometer-registry-prometheus`. Exposed at `/actuator/prometheus`:

| Metric | Source | Type |
|--------|--------|------|
| `http.server.requests` | Spring WebFlux | Timer |
| `resilience4j.circuitbreaker.*` | Resilience4j | Counter, Gauge |
| `jvm.*` | JVM Micrometer | Various |

No custom metrics code.

### Distributed Tracing

OpenTelemetry Java agent auto-instruments Netty HTTP server and client at runtime. Trace context propagated to upstream services via `traceparent` headers. No custom tracing code.

### Health Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Overall health |
| `/actuator/health/liveness` | Process alive? |
| `/actuator/health/readiness` | Ready for traffic? |
| `/actuator/info` | Build metadata |
| `/actuator/prometheus` | Metrics scrape |

No custom health indicators — gateway has no external dependencies to check.

---

## 9. Configuration Model

### Precedence

```
1. Environment variables / K8s secrets    (highest)
2. application.yml                         (base)
```

No custom `@ConfigurationProperties` — everything uses Spring Boot native configuration keys.

### Key Properties

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8000 | HTTP listen port |
| `gateway.auth.mode` | `mock` | Auth mode (`mock` or `remote`) |
| `spring.cloud.gateway.httpclient.response-timeout` | 5s | Global upstream timeout |
| `spring.codec.max-in-memory-size` | 256KB | Request body size limit |

---

## 10. Package Structure

```
gateway
├── GatewayApplication.java              # @SpringBootApplication entry point
├── config/
│   └── AuthConfig.java                  # Conditionally selects AuthenticationProvider bean
├── auth/
│   ├── AuthenticationProvider.java      # Strategy interface: Mono<AuthenticationResult> authenticate(ServerWebExchange)
│   ├── AuthenticationResult.java        # Record: authenticated, subject, roles, permissions, claims
│   ├── MockAuthenticationProvider.java  # Returns authenticated=true, zero I/O
│   └── RemoteAuthenticationProvider.java  # Throws UnsupportedOperationException (future integration)
├── filter/
│   ├── AuthenticationGlobalFilter.java  # GlobalFilter (order -75), runs auth before route matching
│   └── CorrelationIdGlobalFilter.java   # GlobalFilter (order -100), generates/propagates X-Correlation-ID
├── web/
│   └── FallbackController.java          # Returns structured 503 on circuit breaker open
└── common/
    ├── exception/
    │   └── GlobalErrorHandler.java      # ErrorWebExceptionHandler returning JSON error bodies
    └── util/
        └── HeaderConstants.java         # X-Correlation-ID constant
```

**Total custom classes: 11**

### Dependency Rules

```
config  → auth
filter  → auth, common
web     → common
common  → (none)
```

No circular dependencies.

---

## 11. Coding Standards

### Principles

- **Reactive first**: `Mono<T>` / `Flux<T>` everywhere. No blocking calls.
- **Immutable by default**: Java `record` for DTOs, `final` fields for everything else.
- **No nulls**: `Mono.empty()` or `Optional` instead of null.
- **Constructor injection**: No field injection (`@Autowired` on fields).
- **Small classes**: One responsibility. < 100 lines unless unavoidable.
- **Configuration over code**: If SCG has a built-in filter for it, use it.

### Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Global filters | `{Purpose}GlobalFilter` | `AuthenticationGlobalFilter` |
| Controllers | `{Resource}Controller` | `FallbackController` |
| Config classes | `{Domain}Config` | `AuthConfig` |
| Error handlers | `Global{Type}Handler` | `GlobalErrorHandler` |
| Test classes | `{ClassUnderTest}Test` | `AuthenticationGlobalFilterTest` |

### Reactor Best Practices

- `Hooks.enableAutomaticContextPropagation()` in main class for MDC bridging
- `.flatMap()` for async, `.map()` for sync — never nest
- `.onErrorResume()` or `.onErrorMap()` on every risky chain
- Use Reactor `Context` for request-scoped data, not `ThreadLocal`
- `Mono.defer()` for lazy evaluation

---

## 12. Deployment

### Docker

Multi-stage build produces a minimal runtime image with non-root user:

| Stage | Base Image | Purpose |
|-------|-----------|---------|
| Builder | `eclipse-temurin:26-jdk` | Compile and package |
| Runtime | `eclipse-temurin:26-jre` | Run the JAR |

```bash
docker build -t api-gateway .
docker run -p 8000:8000 api-gateway
```

### Graceful Shutdown

- `server.shutdown=graceful` — drain in-flight requests
- `spring.lifecycle.timeout-per-shutdown-phase=30s` — max drain window
