# API Gateway — Software Design Document

> **Project**: API Gateway — front-door proxy for a microservices platform  
> **Version**: 1.0  
> **Status**: Draft

---

## Table of Contents

1. [Vision and Goals](#1-vision-and-goals)
2. [Scope and Non-goals](#2-scope-and-non-goals)
3. [Functional Requirements](#3-functional-requirements)
4. [Non-functional Requirements](#4-non-functional-requirements)
5. [High-level Architecture](#5-high-level-architecture)
6. [Component Diagram](#6-component-diagram)
7. [Request Lifecycle](#7-request-lifecycle)
8. [Filter Pipeline](#8-filter-pipeline)
9. [Module and Package Structure](#9-module-and-package-structure)
10. [Routing Engine Design](#10-routing-engine-design)
11. [Security Design](#11-security-design)
12. [Rate Limiting Design](#12-rate-limiting-design)
13. [Resilience Design](#13-resilience-design)
14. [Observability Design](#14-observability-design)
15. [Configuration Model](#15-configuration-model)
16. [Deployment Architecture](#16-deployment-architecture)
17. [Folder Structure](#17-folder-structure)
18. [Coding Standards](#18-coding-standards)
19. [Testing Strategy](#19-testing-strategy)
20. [Development Roadmap](#20-development-roadmap)

---

## 1. Vision and Goals

### Vision

Build a lightweight, secure, and observable API Gateway that acts as the single entry point for all external and internal client traffic into the platform's microservices ecosystem.

### Goals

| Goal | Description |
|------|-------------|
| **Security-first** | Validate every request at the edge via JWT and API key checks before upstream routing. |
| **Resilient by design** | Protect upstream services from cascading failures using circuit breakers, retries, timeouts, and rate limiting. |
| **Observable** | Provide structured logs, metrics, and distributed traces so operators can debug, monitor, and alert in production. |
| **Performant** | Handle thousands of requests per second per pod with sub-10ms p99 added latency. |
| **Operable** | Expose health, readiness, and admin endpoints; support dynamic route updates without restarts. |
| **Evolvable** | Feature-based modular structure that lets teams add new filters, routes, and authentication schemes without deep refactoring. |

---

## 2. Scope and Non-goals

### In Scope

- HTTP(S) request routing to upstream services
- JWT validation (RS256/ES256 via JWKS endpoint)
- API key validation (hashed keys in PostgreSQL)
- Rate limiting (Redis-backed token bucket, per-user/route/IP)
- Resilience patterns: circuit breaker, retry, timeout, fallback
- Request/response header manipulation
- CORS per-route configuration
- Correlation ID generation and propagation
- Structured JSON logging (console + log aggregator)
- Micrometer metrics + Prometheus scrape endpoint
- OpenTelemetry distributed tracing (W3C TraceContext)
- Kubernetes health probes (/actuator/health, /actuator/info)
- Admin endpoints for route management and gateway status
- Route-level configuration via YAML and optionally Redis-backed dynamic routes

### Non-goals (explicitly out of scope)

- **Service mesh replacement**: The gateway does not handle mTLS between pods, TCP traffic, or gRPC streaming.
- **Message brokering**: Not a replacement for Kafka/RabbitMQ.
- **Database migrations**: API key provisioning is handled by a separate admin service.
- **UI serving**: The gateway proxies to frontend services but does not serve static assets directly (CDN preferred).
- **Custom DNS or service discovery**: Relies on Kubernetes DNS + Eureka/Consul if needed.
- **GraphQL**: Not handled at the gateway layer (terminated upstream).
- **WebSocket proxy**: Out of scope for V1 (can be added later with Spring Cloud Gateway's WebSocket support).
- **Request body manipulation**: Not needed for this gateway's responsibilities.
- **Request aggregation**: No backend-for-frontend (BFF) pattern here — keep the gateway thin.

---

## 3. Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-01 | Gateway must route requests based on path, method, and headers. | P0 |
| FR-02 | Gateway must validate JWT tokens on protected routes. | P0 |
| FR-03 | Gateway must validate API keys on key-protected routes. | P0 |
| FR-04 | Gateway must enforce rate limits per user, API key, and IP. | P0 |
| FR-05 | Gateway must apply circuit breakers per upstream route. | P0 |
| FR-06 | Gateway must retry failed upstream requests on transient errors. | P0 |
| FR-07 | Gateway must enforce upstream request timeouts. | P0 |
| FR-08 | Gateway must add, remove, and transform HTTP headers. | P1 |
| FR-09 | Gateway must handle CORS preflight based on per-route config. | P1 |
| FR-10 | Gateway must generate and propagate correlation IDs. | P0 |
| FR-11 | Gateway must emit structured JSON logs with correlation ID. | P0 |
| FR-12 | Gateway must expose health and readiness endpoints. | P0 |
| FR-13 | Gateway must expose Prometheus metrics. | P0 |
| FR-14 | Gateway must expose distributed trace spans. | P0 |
| FR-15 | Gateway must support dynamic route updates via Redis. | P1 |
| FR-16 | Gateway must reject unauthenticated requests with 401. | P0 |
| FR-17 | Gateway must reject unauthorized requests with 403. | P0 |
| FR-18 | Gateway must reject excessively large request bodies. | P1 |
| FR-19 | Gateway must reject malformed request payloads. | P1 |

---

## 4. Non-functional Requirements

| ID | Requirement | Target | Rationale |
|----|-------------|--------|-----------|
| NFR-01 | **Latency** | p99 < 15ms overhead (total < 50ms for proxy) | Gateway must not become a bottleneck. |
| NFR-02 | **Throughput** | 5000+ req/s per pod (4 vCPU) | Horizontal scaling adds capacity linearly. |
| NFR-03 | **Availability** | 99.99% uptime (4-nines) | Stateless design + K8s multi-replica + HPA. |
| NFR-04 | **Scalability** | Horizontal via HPA (2–20 pods) | Stateless, all state in Redis. |
| NFR-05 | **Startup time** | < 10 seconds | Fast rollouts, quick recovery. |
| NFR-06 | **Graceful shutdown** | Drain in-flight requests | No dropped connections during rolling update. |
| NFR-07 | **Security** | OWASP Top 10 mitigated | JIT token validation, no PII in logs. |
| NFR-08 | **Observability** | 100% of requests traced | Correlate logs/metrics/traces via trace ID. |
| NFR-09 | **Resource limits** | CPU < 3.0 / mem < 512 MiB per pod | Predictable cost, no noisy neighbors. |
| NFR-10 | **Config change** | Hot-reload in < 5 seconds | Dynamic route updates without restart. |
| NFR-11 | **Test coverage** | > 85% line coverage | Production confidence. |

### Design Tensions Resolved

| Tension | Decision | Rationale |
|---------|----------|-----------|
| Latency vs. security | Validate JWT + API key for every request; cache JWKS with TTL | Security cannot be compromised for performance. Cache minimizes overhead. |
| Observability vs. speed | Sample traces at 10% for high-traffic routes | P99 latency target accounts for trace overhead. Sampling preserves budget. |
| Dynamic routes vs. simplicity | Use YAML-based config by default, Redis-backed for dynamic needs | YAML for static routes is simpler and auditable. Redis for dynamic when needed. |
| Rich features vs. thin gateway | Keep filters lean; delegate heavy processing upstream | Gateway is a routing layer, not a BFF or aggregation layer. |

---

## 5. High-level Architecture

```
                    ┌─────────────┐
                    │   Clients   │
                    │  (App/Browser) │
                    └──────┬──────┘
                           │ HTTPS
                           ▼
                  ┌─────────────────┐
                  │   K8s Ingress   │
                  │ (nginx-ingress) │
                  └────────┬────────┘
                           │ HTTPS (TLS termination)
                           ▼
            ┌─────────────────────────────┐
            │     API Gateway (xN pods)   │
            │  Spring Cloud Gateway 4.x   │
            │  Netty / WebFlux / Reactor  │
            │  Java 21                    │
            │                             │
            │  ┌─── Filter Pipeline ───┐  │
            │  │ Pre → Route → Post    │  │
            │  └───────────────────────┘  │
            └────┬──────┬──────┬──────────┘
                 │      │      │
           ┌─────┘      │      └─────┐
           ▼            ▼            ▼
     ┌──────────┐ ┌──────────┐ ┌──────────┐
     │ Service-A │ │ Service-B │ │ Service-C │
     │ (Upstream)│ │ (Upstream)│ │ (Upstream)│
     └──────────┘ └──────────┘ └──────────┘

     ┌─────────────────────────────────────┐
     │         Shared Infrastructure       │
     │  Redis (rate limit + route store)   │
     │  PostgreSQL (API key store)         │
     │  OpenTelemetry Collector            │
     │  Prometheus + Grafana               │
     │  Loki / Splunk / Datadog            │
     └─────────────────────────────────────┘
```

### Architecture Tenets

1. **Stateless**: No session data in the gateway pod. All shared state lives in Redis.
2. **Reactive end-to-end**: Netty event loop does the work; no thread pool blocking.
3. **Immutable config**: Routes and filters configured via YAML at deploy time; dynamic overrides in Redis.
4. **Fail-fast**: Authentication and validation failures reject immediately — no upstream round-trip on bad auth.
5. **Defensive deprecation**: Circuit breakers and rate limits protect upstreams from the gateway, not the other way around.

---

## 6. Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        API Gateway (Pod)                            │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   Filter Chain                                │   │
│  │  ┌──────────┐ ┌─────────┐ ┌────────┐ ┌───────┐ ┌─────────┐ │   │
│  │  │ CORS     │→│Corr ID  │→│ Auth   │→│Rate   │→│Routing  │ │   │
│  │  │ Filter   │ │ Filter  │ │ Filter │ │Limiter│ │ Filter  │ │   │
│  │  └──────────┘ └─────────┘ └────────┘ └───────┘ └────┬────┘ │   │
│  │                                                      │       │   │
│  │  ┌───────────────────────────────────────────────────▼─────┐ │   │
│  │  │            Route Execution                              │ │   │
│  │  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌────────────┐   │ │   │
│  │  │  │Retry │→│CB    │→│Timer │→│Proxy │→│ Post-filters│   │ │   │
│  │  │  │Filter│ │Filter│ │Filter│ │Filter│ │             │   │ │   │
│  │  │  └──────┘ └──────┘ └──────┘ └──────┘ └────────────┘   │ │   │
│  │  └─────────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐              │
│  │Route     │ │JWT       │ │Rate Limit│ │Resilience│              │
│  │Locator   │ │Validator │ │(Redis)   │ │4j        │              │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐              │
│  │Micrometer│ │OpenTele  │ │Actuator  │ │Logback   │              │
│  │Metrics   │ │metry     │ │Endpoints │ │(JSON)    │              │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘              │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Components

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| **RouteLocator** | Resolves incoming request to a route definition | `RouteLocator` + `RouteDefinitionLocator` |
| **AuthFilter** | Validates JWT or API key | Custom `GatewayFilter` |
| **RateLimiter** | Enforces token bucket rate limits | Redis + custom filter |
| **Resilience Filters** | CB, retry, timeout | Resilience4j + `SpringCloudCircuitBreakerFilterFactory` |
| **CorrelationFilter** | Generate/propagate trace IDs | Custom `GatewayFilter` |
| **MetricsFilter** | Record request count, latency, status | Micrometer `Timer` + `Counter` |
| **TracingFilter** | Create spans for each request phase | OpenTelemetry SDK |
| **RouteDefinitionRepo** | Dynamic route store | Redis-backed `RouteDefinitionRepository` |

---

## 7. Request Lifecycle

```
Client                    Gateway                       Upstream
  │                         │                              │
  │──── HTTPS /api/v1/users ──►                              │
  │                         │                              │
  │                     ┌───▼──────────────┐               │
  │                     │ 1. Netty reads   │               │
  │                     │    HTTP request  │               │
  │                     └───┬──────────────┘               │
  │                         │                              │
  │                     ┌───▼──────────────┐               │
  │                     │ 2. CORS check    │               │
  │                     │    (preflight?)  │               │
  │                     └───┬──────────────┘               │
  │                         │                              │
  │                     ┌───▼──────────────┐               │
  │                     │ 3. Correlation   │               │
  │                     │    ID filter     │               │
  │                     │    (gen/forward) │               │
  │                     └───┬──────────────┘               │
  │                         │                              │
  │                     ┌───▼──────────────┐               │
  │                     │ 4. Auth filter   │               │
  │                     │    JWT validation│               │
  │                     │    OR API key    │               │
  │                     │    validation    │               │
  │                     └───┬──────────────┘               │
  │                         │                              │
  │                     ┌───▼──────────────┐               │
  │                     │ 5. Rate limiter  │               │
  │                     │    Redis token   │               │
  │                     │    bucket check  │               │
  │                     └───┬──────────────┘               │
  │                         │                              │
  │                     ┌───▼──────────────┐               │
  │                     │ 6. Route match   │               │
  │                     │    (path/method/ │               │
  │                     │     headers)     │               │
  │                     └───┬──────────────┘               │
  │                         │                              │
  │                     ┌───▼──────────────┐               │
  │                     │ 7. Resilience    │               │
  │                     │    CB check →    │               │
  │                     │    Retry →       │               │
  │                     │    Timeout       │               │
  │                     └───┬──────────────┘               │
  │                         │                              │
  │                     ┌───▼──────────────┐               │
  │                     │ 8. Proxy filter  │──────────────►│
  │                     │    (forward req) │               │
  │                     │                  │◄──────────────│
  │                     │    Response      │               │
  │                     └───┬──────────────┘               │
  │                         │                              │
  │                     ┌───▼──────────────┐               │
  │                     │ 9. Post-filters  │               │
  │                     │    Header strip  │               │
  │                     │    Metrics rec   │               │
  │                     └───┬──────────────┘               │
  │                         │                              │
  │◄──── HTTP Response ──────                              │
```

### Phase Details

| Phase | Filter | Action | Error Response |
|-------|--------|--------|----------------|
| 1 | Netty | Parse HTTP request, buffer headers | 400 (malformed) |
| 2 | CorsFilter | Handle preflight, check origins | 403 (origin denied) |
| 3 | CorrelationIdFilter | Generate/forward X-Correlation-ID | — |
| 4 | JwtAuthFilter / ApiKeyFilter | Validate token, extract principal | 401 (invalid/missing), 403 (forbidden) |
| 5 | RateLimitingFilter | Check Redis token bucket | 429 (Too Many Requests) |
| 6 | Route Matching | Match route by predicate | 404 (No matching route) |
| 7 | CircuitBreakerFilter | Check circuit state | 503 (Circuit open) |
| 8 | RetryFilter + Timeout | Proxy with retry + deadline | 504 (Upstream timeout) |
| 9 | ResponseHeaderFilter | Strip sensitive headers | — |

---

## 8. Filter Pipeline

### Filter Ordering

The Spring Cloud Gateway filter chain has two phases: **pre** (before routing) and **post** (after response from upstream). Filters are ordered by `@Order` value.

```
Order    Filter                          Phase     Type
───────  ────────────────────────────    ─────     ─────────────────
-100     CorsFilter                      Pre      GlobalFilter
-90      CorrelationIdFilter             Pre      GlobalFilter
-80      RequestLoggingFilter            Pre      GlobalFilter
-70      JwtAuthenticationFilter         Pre      GatewayFilter (per-route)
-60      ApiKeyAuthenticationFilter      Pre      GatewayFilter (per-route)
-50      RateLimitingFilter              Pre      GatewayFilter (per-route)
-40      HeaderTransformationFilter      Pre      GatewayFilter (per-route)

          ─── Route Execution ───

+10      CircuitBreakerFilter            Pre/Post GatewayFilter (per-route)
+20      RetryFilter                     Pre/Post GatewayFilter (per-route)
+30      TimeoutFilter                   Pre      GatewayFilter (per-route)
+40      ResponseHeaderFilter            Post     GatewayFilter (per-route)
+50      MetricsFilter                   Post     GlobalFilter
+60      TracingFilter                   Post     GlobalFilter
```

### Filter Type Definitions

#### Global Filters (applied to all routes)

| Filter | Purpose |
|--------|---------|
| `CorsFilter` | Validates Origin header, handles preflight OPTIONS |
| `CorrelationIdFilter` | Generates X-Correlation-ID if absent; forwards to upstream |
| `RequestLoggingFilter` | Logs incoming request (method, path, correlation ID) at INFO |
| `MetricsFilter` | Records `http_requests_total`, `http_request_duration_seconds` |
| `TracingFilter` | Ends main span, records attributes |

#### Per-route Gateway Filters (applied to matched routes only)

| Filter | Purpose |
|--------|---------|
| `JwtAuthenticationFilter` | Extracts JWT from Authorization header, validates, populates SecurityContext |
| `ApiKeyAuthenticationFilter` | Validates X-API-Key against PostgreSQL hash |
| `RateLimitingFilter` | Enforces token bucket rate limit via Redis |
| `HeaderTransformationFilter` | Adds/removes/rewrites headers per route config |
| `CircuitBreakerFilter` | Wraps route in Resilience4j circuit breaker |
| `RetryFilter` | Retries upstream on 5xx / timeout |
| `TimeoutFilter` | Sets per-route response timeout |
| `ResponseHeaderFilter` | Strips sensitive upstream headers (Server, X-Powered-By) |

### Custom Filter Factory Pattern

Every custom filter implements `GatewayFilterFactory` (or extends `AbstractGatewayFilterFactory`). This is the standard Spring Cloud Gateway pattern.

```java
// Template for all custom filters
@Component
public class JwtAuthenticationFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationFilterFactory.Config> {

    public JwtAuthenticationFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Pre-filter logic
            // ...
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                // Post-filter logic
                // ...
            }));
        };
    }

    @Data
    public static class Config {
        private List<String> requiredRoles;
        private boolean optional;
    }
}
```

### Why Custom Filters Over Built-in?

| Built-in | Limitation | Decision |
|----------|------------|----------|
| `RequestRateLimiterGatewayFilterFactory` | Coupled to Redis + rate limiter config | Write custom for flexible key resolver |
| `SpringCloudCircuitBreakerFilterFactory` | Good, but limited fallback customization | Use as-is with custom fallback URI |
| `RetryGatewayFilterFactory` | Does not support reactive retry backoff | Use resilience4j-spring-boot3 directly |
| `AddRequestHeaderGatewayFilterFactory` | Works fine | Use as-is for simple cases |

---

## 9. Module and Package Structure

### Single-module (no multi-module)

**Decision: Single Gradle module.** A multi-module setup adds build complexity that is not justified here. The gateway is a single deployable unit with clear package boundaries.

### Package Layout

```
com.example.gateway
├── GatewayApplication.java                # @SpringBootApplication
├── config/                                # Global Spring configuration
│   ├── GatewayConfig.java                 # Bean declarations, filter registration
│   ├── RedisConfig.java                   # Redis connection, serialization
│   ├── OpenTelemetryConfig.java           # OTel SDK init, exporter config
│   └── Resilience4jConfig.java            # CB, retry, timeout config beans
│
├── route/                                 # Route definition and matching
│   ├── RouteConfig.java                   # @Configuration RouteLocator builder
│   ├── RouteValidator.java                # Validates route definitions at startup
│   └── dynamic/
│       ├── RedisRouteDefinitionRepository.java  # Route store in Redis
│       └── RouteRefreshListener.java            # Listens for route change events
│
├── security/                              # Authentication and authorization
│   ├── auth/
│   │   ├── JwtAuthenticationFilterFactory.java    # GatewayFilterFactory for JWT
│   │   ├── JwtTokenValidator.java                 # RS256/ES256 JWKS validation
│   │   ├── ApiKeyFilterFactory.java               # GatewayFilterFactory for API keys
│   │   ├── ApiKeyRepository.java                  # Reactive repo for API keys (PG)
│   │   └── SecurityContextExtractor.java          # Principal extraction helpers
│   ├── cors/
│   │   └── CorsConfiguration.java        # Per-route CORS config
│   └── model/
│       ├── AuthenticatedUser.java        # Principal model
│       └── ApiKey.java                   # API key entity
│
├── ratelimit/                             # Rate limiting
│   ├── RateLimitingFilterFactory.java     # GatewayFilterFactory
│   ├── RateLimitKeyResolver.java          # Interface + impls (user, ip, key)
│   └── TokenBucketRateLimiter.java        # Redis-based token bucket (Lua script)
│
├── resilience/                            # Resilience patterns
│   ├── CircuitBreakerFilterFactory.java   # Custom CB filter
│   ├── FallbackController.java            # Default fallback responses
│   └── TimeoutConfig.java                 # Per-route timeout config
│
├── filter/                                # General-purpose filters
│   ├── CorrelationIdFilterFactory.java    # X-Correlation-ID management
│   ├── HeaderTransformationFilterFactory.java # Add/remove/rewrite headers
│   ├── RequestValidationFilterFactory.java    # Body size, content-type checks
│   └── ResponseHeaderFilterFactory.java       # Strip sensitive response headers
│
├── observability/                         # Metrics, tracing, logging
│   ├── MetricsFilterFactory.java          # Micrometer metrics recording
│   ├── TracingFilterFactory.java          # OpenTelemetry span management
│   ├── LoggingConfig.java                 # Logback JSON encoder config
│   └── GatewayHealthIndicator.java        # Custom health checks (Redis, PG)
│
├── admin/                                 # Admin endpoints
│   ├── AdminController.java               # Route CRUD, cache flush, status
│   └── AdminSecurityConfig.java           # Admin endpoint auth (internal-only)
│
├── common/                                # Shared utilities
│   ├── exception/
│   │   ├── GatewayException.java          # Base exception
│   │   └── GlobalErrorHandler.java        # ErrorWebExceptionHandler
│   └── util/
│       ├── HeaderConstants.java           # Header name constants
│       └── ReactiveRequestContext.java    # Reactor context utilities
│
└── model/                                 # Configuration property classes
    ├── GatewayProperties.java             # @ConfigurationProperties root
    ├── RouteConfigProperties.java         # Per-route config model
    ├── SecurityProperties.java            # JWT, API key settings
    └── RateLimitProperties.java           # Rate limit defaults
```

### Package Dependency Rules

```
route → config, common
security → config, common
ratelimit → config, common
resilience → config, common
filter → config, common
observability → config, common
admin → security, common, config
```

No package depends on another feature package (e.g., `security` never imports `ratelimit`). Cross-cutting concerns like correlation ID flow through `ServerWebExchange` attributes, not direct imports.

---

## 10. Routing Engine Design

### How Routes Are Defined

Routes are defined as YAML under `spring.cloud.gateway.routes` with custom metadata for security, rate limiting, and resilience.

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: users-api
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
            - Method=GET,POST,PUT,DELETE
          filters:
            - name: JwtAuthentication
              args:
                requiredRoles: ["USER", "ADMIN"]
            - name: RateLimiting
              args:
                keyType: USER
                capacity: 100
                refillRate: 50
            - name: CircuitBreaker
              args:
                name: usersCB
                fallbackUri: forward:/fallback/users
            - name: Retry
              args:
                maxRetries: 3
                statusCodes: 500,502,503
            - name: Timeout
              args:
                durationMs: 5000
          metadata:
            cors:
              allowedOrigins: https://app.example.com
              allowedMethods: GET,POST
```

### Route Resolution Algorithm

```
1. Request enters Netty → ServerWebExchange created
2. RouteLocator.getRoutes() → Flux<Route> (merged from YAML + Redis)
3. For each Route, predicates are evaluated in order
4. First route matching ALL predicates wins
5. Route filters are combined: global → route-level → default → post
6. If no route matches → 404
```

### Dynamic Routes via Redis

```java
@Component
public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {

    private final ReactiveRedisTemplate<String, RouteDefinition> redisTemplate;
    private static final String ROUTE_KEY_PREFIX = "gateway:route:";

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return redisTemplate.keys(ROUTE_KEY_PREFIX + "*")
            .flatMap(redisTemplate.opsForValue()::get);
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(r ->
            redisTemplate.opsForValue()
                .set(ROUTE_KEY_PREFIX + r.getId(), r));
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id ->
            redisTemplate.delete(ROUTE_KEY_PREFIX + id).then());
    }
}
```

### Route Refresh Mechanism

- `RouteRefreshListener` subscribes to Redis Pub/Sub channel `gateway:route:changes`
- On message: publishes `RefreshRoutesEvent` → `CachingRouteLocator` reloads
- Fallback: periodic refresh every 30 seconds

### Design Decision: YAML-First, Redis Second

| Approach | Pros | Cons |
|----------|------|------|
| YAML-only | Simple, auditable, GitOps flow | Requires redeploy for route changes |
| Redis-only | Dynamic, no redeploy | No version control, harder audit |
| **YAML + Redis (merged)** | Best of both | Merge conflict risk (resolved: Redis wins) |

**Decision**: Routes are defined in YAML for static infrastructure routes. Redis-backed `RouteDefinitionRepository` overlays dynamic routes. Redis routes take precedence by ID — if Redis has a route with the same ID as YAML, Redis wins.

---

## 11. Security Design

### Authentication Flow

#### JWT Authentication

```
Client                          Gateway                         IdP (JWKS)
  │                               │                               │
  │── Authorization: Bearer <JWT> ──►                               │
  │                               │                               │
  │                          ┌────▼────┐                         │
  │                          │ Parse   │                         │
  │                          │ Header  │                         │
  │                          └────┬────┘                         │
  │                          ┌────▼────┐                         │
  │                          │ Fetch   │──(if cache miss)───────►│
  │                          │ JWKS    │◄────(cached 5 min)──────│
  │                          └────┬────┘                         │
  │                          ┌────▼────┐                         │
  │                          │ Validate│                         │
  │                          │ - sig   │                         │
  │                          │ - exp   │                         │
  │                          │ - iss   │                         │
  │                          │ - aud   │                         │
  │                          └────┬────┘                         │
  │                          ┌────▼────┐                         │
  │                          │ Extract │                         │
  │                          │ roles   │                         │
  │                          │ sub/uid │                         │
  │                          └────┬────┘                         │
  │                          ┌────▼────┐                         │
  │                          │ Populate│                         │
  │                          │ Security│                         │
  │                          │ Context │                         │
  │                          └────┬────┘                         │
  │                          Continue to next filter
```

**JWKS Caching Strategy**:
- Cache JWKS response with 5-minute TTL
- Cache per JWKS URL (support multiple issuers)
- On validation failure: evict cache, retry once before rejecting
- Evict on NTP-reported clock skew

#### API Key Authentication

```
Client                          Gateway                         PostgreSQL
  │                               │                               │
  │── X-API-Key: sk_live_xxxxx ──►                               │
  │                               │                               │
  │                          ┌────▼────┐                         │
  │                          │ Hash    │                         │
  │                          │ key     │                         │
  │                          │ (SHA256)│                         │
  │                          └────┬────┘                         │
  │                          ┌────▼────┐                         │
  │                          │ Lookup  │────────────────────────►│
  │                          │ hash    │◄────────────────────────│
  │                          │ in PG   │ (key metadata + status) │
  │                          └────┬────┘                         │
  │                          ┌────▼────┐                         │
  │                          │ Check   │                         │
  │                          │ active  │                         │
  │                          │ + scopes│                         │
  │                          └────┬────┘                         │
  │                          ┌────▼────┐                         │
  │                          │ Cache   │                         │
  │                          │ (5 min) │                         │
  │                          │ in Redis│                         │
  │                          └────┬────┘                         │
```

**API Key Storage**:
- PostgreSQL table: `api_keys(id, key_hash, prefix, label, scopes[], status, created_at, expires_at)`
- `key_hash` is SHA-256 of the full key
- `prefix` is first 8 chars (for identification in logs)
- Raw key is shown once at creation, never stored
- Redis cache with 5-minute TTL to avoid PG load

### Authorization Model

| Check | Where | Granularity |
|-------|-------|-------------|
| Authentication (who) | Gateway filter | JWT sub / API key ID |
| Role check (can they?) | Gateway filter | Route config `requiredRoles` |
| Scope check (what access?) | Gateway filter | API key scopes vs route required scopes |
| Downstream auth | Upstream service | Full RBAC in service layer |

### CORS Design

```yaml
# Per-route CORS in route metadata
metadata:
  cors:
    allowedOrigins: "https://app.example.com,https://admin.example.com"
    allowedMethods: "GET,POST,PUT,DELETE,PATCH"
    allowedHeaders: "Authorization,Content-Type,X-Correlation-ID"
    maxAge: 1800
```

- CORS is evaluated **before** auth filters — preflight OPTIONS never reach auth
- Global fallback CORS policy for unmatched routes
- No wildcard origins with credentials

### Security Headers (added to every response)

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0 (deprecated but harmless)
Strict-Transport-Security: max-age=31536000; includeSubDomains
Cache-Control: no-store
Content-Security-Policy: default-src 'none'
```

---

## 12. Rate Limiting Design

### Algorithm: Token Bucket

```
Parameters:
  capacity     → max burst size (bucket depth)
  refillRate   → tokens added per second
  refillPeriod → 1 second

Operation:
  On each request:
    1. last_refill_time = Redis GET gate:ratelimit:{key}:time
    2. tokens = Redis GET gate:ratelimit:{key}:tokens
    3. elapsed = now - last_refill_time
    4. tokens = min(capacity, tokens + elapsed * refillRate)
    5. If tokens >= 1:
         tokens -= 1
         ALLOW
       Else:
         DENY (429)
    6. Redis SET gate:ratelimit:{key}:tokens tokens
    7. Redis SET gate:ratelimit:{key}:time now
```

### Redis Lua Script (atomic, no race conditions)

```lua
-- KEYS[1] = rate:limiter:{key}.tokens
-- KEYS[2] = rate:limiter:{key}.timestamp
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refillRate (tokens per second)
-- ARGV[3] = now (epoch seconds)

local tokens = redis.call("GET", KEYS[1])
local lastRefill = redis.call("GET", KEYS[2])

if tokens == false then
    tokens = ARGV[1]
    lastRefill = ARGV[3]
else
    local elapsed = tonumber(ARGV[3]) - tonumber(lastRefill)
    local refill = math.floor(elapsed * tonumber(ARGV[2]))
    tokens = math.min(tonumber(ARGV[1]), tonumber(tokens) + refill)
    lastRefill = ARGV[3]
end

if tokens >= 1 then
    redis.call("SET", KEYS[1], tokens - 1)
    redis.call("SET", KEYS[2], lastRefill)
    return 1  -- ALLOW
else
    return 0  -- DENY
end
```

### Key Resolution Strategy

| Key Type | Key Value | Example |
|----------|-----------|---------|
| USER | JWT `sub` claim | `user:user_abc123` |
| API_KEY | API key prefix | `apikey:sk_live_a1` |
| IP | Client IP (X-Forwarded-For) | `ip:203.0.113.42` |
| COMBINED | user + route ID | `user:u1:route:users-api` |

```java
public interface RateLimitKeyResolver {
    Mono<String> resolve(ServerWebExchange exchange);
}

@Component
public class UserRateLimitKeyResolver implements RateLimitKeyResolver {
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal()
            .map(p -> "user:" + p.getName())
            .switchIfEmpty(Mono.error(...));
    }
}
```

### Rate Limit Response

```json
HTTP 429 Too Many Requests
Retry-After: 30
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1700000000
```

### Rate Limit Configuration Model

```yaml
# Global defaults
gateway:
  rate-limit:
    default-capacity: 100
    default-refill-rate: 50
    key-type: USER       # USER | API_KEY | IP | COMBINED

# Per-route override in route filters
spring:
  cloud:
    gateway:
      routes:
        - id: compute-heavy-api
          filters:
            - name: RateLimiting
              args:
                keyType: USER
                capacity: 20
                refillRate: 5
```

---

## 13. Resilience Design

### Circuit Breaker

**Library**: Resilience4j via `spring-cloud-circuitbreaker-resilience4j`

**Configuration** (per route):

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        slowCallRateThreshold: 60
        slowCallDurationThreshold: 4s
        recordExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignoreExceptions:
          - com.example.gateway.common.exception.GatewayException
    instances:
      usersCB:
        baseConfig: default
      ordersCB:
        baseConfig: default
```

**State machine**:

```
CLOSED → (failure rate > threshold) → OPEN → (wait time elapsed) → HALF_OPEN
                                                                    ↓
                                                     (success) → CLOSED
                                                     (failure) → OPEN
```

**Custom fallback**:

```java
@Component
public class FallbackController {
    @RequestMapping("/fallback/{routeId}")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(
            @PathVariable String routeId, ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(503).body(Map.of(
            "status", 503,
            "error", "Service temporarily unavailable",
            "route", routeId,
            "correlationId", exchange.getAttribute("correlationId")
        )));
    }
}
```

### Retry

**Library**: Resilience4j retry (not Spring RetryGatewayFilterFactory)

**Strategy**:

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Max attempts | 3 | Enough for transient recovery, not amplifying load |
| Backoff | Exponential: 200ms, 400ms, 800ms | Spread retries across time |
| Jitter | ±100ms | Avoid thundering herd |
| Retry on | 5xx, IOException, TimeoutException | Safe for idempotent upstreams |
| Do NOT retry on | 4xx, POST/PUT/DELETE (unless idempotent) | Avoid double-writes |

```yaml
resilience4j:
  retry:
    instances:
      defaultRetry:
        maxRetryAttempts: 3
        waitDuration: 200ms
        exponentialBackoffMultiplier: 2
        enableExponentialBackoff: true
        retryExceptions:
          - java.io.IOException
          - org.springframework.cloud.gateway.support.NotFoundException
        ignoreExceptions:
          - com.example.gateway.common.exception.GatewayException
```

### Timeout

**Approach**: Per-route timeout applied as a filter wrapping the proxy exchange.

```java
public class TimeoutFilterFactory extends AbstractGatewayFilterFactory<TimeoutFilterFactory.Config> {
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> chain.filter(exchange)
            .timeout(Duration.ofMillis(config.getDurationMs()))
            .onErrorResume(TimeoutException.class, ex -> {
                exchange.getResponse().setStatusCode(HttpStatus.GATEWAY_TIMEOUT);
                return exchange.getResponse().setComplete();
            });
    }
}
```

### Resilience Strategy Matrix

| Pattern | When It Fires | Response |
|---------|--------------|----------|
| Circuit Breaker | Failure rate > 50% in 10-call window | 503 + fallback |
| Retry | 5xx / IOException | Transparent to client (up to 3 attempts) |
| Timeout | Upstream silent > configured duration | 504 Gateway Timeout |
| Rate Limiter | Request rate exceeds configured limit | 429 Too Many Requests |

### Addressing the "Cascading Failure" Problem

The rate limiter protects upstreams from overload. The circuit breaker protects upstreams from a failing downstream dependency. The timeout prevents connections from hanging. Together they form a defense-in-depth layer:

```
Load Spike
  → Rate Limiter rejects at gateway (429)
  → Upstream partially fails
  → Circuit Breaker opens (503)
  → Downstream services are protected
  → Gateway falls back gracefully
```

---

## 14. Observability Design

### 14.1 Structured Logging

**Format**: JSON (Logstash/ECSD format)

```json
{
  "@timestamp": "2026-07-10T12:34:56.789Z",
  "level": "INFO",
  "logger": "com.example.gateway.filter.CorrelationIdFilter",
  "thread": "reactor-http-nio-2",
  "message": "Request received",
  "correlationId": "corr_abc123def456",
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "spanId": "b7ad6b7169203331",
  "method": "GET",
  "path": "/api/v1/users",
  "status": 200,
  "durationMs": 42,
  "clientIp": "203.0.113.42",
  "userId": "user_abc123"
}
```

**MDC Context**: Correlation ID, trace ID, user ID injected via Reactor `Hooks.enableAutomaticContextPropagation()` and `SubscriberContext`.

**Logback config**: Async appender with JSON encoder:

```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdc>true</includeMdc>
        <provider class="net.logstash.logback.composite.loggingevent.ArgumentsJsonProvider"/>
    </encoder>
</appender>

<root level="INFO">
    <appender-ref ref="JSON"/>
</root>
```

### 14.2 Metrics

**Library**: Micrometer (bundled with Spring Boot Actuator + Micrometer registry)

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `gateway.requests.total` | Counter | method, path, status, route | Total HTTP requests |
| `gateway.requests.active` | Gauge | none | In-flight requests |
| `gateway.request.duration` | Timer | method, path, status, route | Request latency |
| `gateway.requests.size.bytes` | DistributionSummary | method, path | Request/response sizes |
| `gateway.ratelimit.remaining` | Gauge | route | Remaining rate limit tokens |
| `gateway.ratelimit.rejected.total` | Counter | route | Rate-limited requests |
| `gateway.circuitbreaker.state` | Gauge | name, state | Circuit breaker state (0=closed, 1=half, 2=open) |
| `gateway.circuitbreaker.calls` | Counter | name, kind(success/failure) | Circuit breaker call outcomes |
| `gateway.jwt.validation.duration` | Timer | issuer | JWT validation latency |

**Export**: `/actuator/prometheus` endpoint consumed by Prometheus.

### 14.3 Distributed Tracing

**Library**: OpenTelemetry with W3C TraceContext propagation.

```
Request enters Gateway
  └─ Span: gateway.request (root)
       ├─ Span: gateway.auth.jwt     (JWT validation)
       ├─ Span: gateway.ratelimit    (rate limit check)
       ├─ Span: gateway.route        (proxy to upstream)
       │    └─ Span: upstream.HTTP   (auto-instrumented by OTel)
       └─ Span: gateway.response     (response processing)
```

**Configuration**:

```yaml
otel:
  service.name: api-gateway
  exporter:
    otlp:
      endpoint: http://otel-collector:4318
      protocol: http/protobuf
  traces:
    sampler: probability-based(0.1)  # 10% sampling for high-volume
  propagators: tracecontext, baggage
```

**Headers propagated upstream**:

```
traceparent: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01
tracestate: platform=production
X-Correlation-ID: corr_abc123def456
```

### 14.4 Health Endpoints

| Endpoint | Purpose | Dependencies Checked |
|----------|---------|---------------------|
| `/actuator/health` | Liveness + Readiness | Redis, PostgreSQL (connection), upstream services (optional) |
| `/actuator/health/liveness` | K8s liveness probe | None (always UP if process is alive) |
| `/actuator/health/readiness` | K8s readiness probe | Redis, PostgreSQL |
| `/actuator/info` | Build info, git commit, Java version | None |
| `/actuator/prometheus` | Metrics scrape | None |

### 14.5 Admin Endpoints

| Endpoint | Method | Purpose | Auth |
|----------|--------|---------|------|
| `/admin/routes` | GET | List all active routes | mTLS or internal network |
| `/admin/routes` | POST | Add or update a route | mTLS or internal network |
| `/admin/routes/{id}` | DELETE | Remove a route | mTLS or internal network |
| `/admin/routes/{id}` | GET | Get route details | mTLS or internal network |
| `/admin/routes/refresh` | POST | Force route refresh | mTLS or internal network |
| `/admin/cache/flush` | POST | Flush JWKS/API key cache | mTLS or internal network |
| `/admin/status` | GET | Gateway health summary | mTLS or internal network |

Admin endpoints are bound to a separate port (e.g., 9090) and not exposed externally.

---

## 15. Configuration Model

### Configuration Sources (ordered by precedence)

```
1. K8s Secret / Environment variables     (highest)
2. Redis dynamic config overrides
3. application-{profile}.yml              (profile-specific)
4. application.yml                        (base)
```

### Application YAML Structure

```yaml
server:
  port: 8080
  netty:
    connection-timeout: 5s
    max-initial-line-length: 8KB
    max-chunk-size: 16KB

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      default-filters:
        - name: CorrelationId
        - name: Metrics
        - name: Tracing
      routes: []  # Defined per-environment

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    timeout: 2s
    connect-timeout: 2s
    lettuce:
      pool:
        max-active: 16
        max-idle: 8
        min-idle: 4

  r2dbc:
    url: r2dbc:postgresql://${PG_HOST:localhost}:${PG_PORT:5432}/${PG_DATABASE:apigateway}
    username: ${PG_USER:gateway}
    password: ${PG_PASSWORD}

gateway:
  cors:
    global-allowed-origins: ${GATEWAY_CORS_ORIGINS:https://app.example.com}
    max-age: 1800

  security:
    jwt:
      issuers:
        - url: https://auth.example.com
          jwks-url: https://auth.example.com/.well-known/jwks.json
          audience: api-gateway
          cache-ttl: 5m
    api-key:
      cache-ttl: 5m

  rate-limit:
    default-capacity: 100
    default-refill-rate: 50
    key-type: USER
    redis-prefix: "gateway:ratelimit"

  resilience:
    circuit-breaker:
      default-sliding-window: 10
      default-failure-threshold: 50
      default-wait-duration: 30s
    retry:
      default-max-attempts: 3
      default-backoff: 200ms
    timeout:
      default-duration: 5s

  observability:
    metrics:
      enabled: true
      prefix: gateway
    tracing:
      enabled: true
      sampling-rate: 0.1
      exporter-endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}
```

### Feature Flag Pattern

```yaml
gateway:
  features:
    jwt-auth: true
    api-key-auth: true
    rate-limiting: true
    circuit-breaker: true
    tracing: true
```

Feature flags allow toggling entire capabilities without code changes. Useful for dark launches and emergency disable.

### Configuration Properties Classes

```java
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    private CorsConfig cors;
    private SecurityConfig security;
    private RateLimitConfig rateLimit;
    private ResilienceConfig resilience;
    private ObservabilityConfig observability;
    private Map<String, Boolean> features;
}
```

---

## 16. Deployment Architecture

### Docker Image

**Build tool**: Jib (no Docker daemon needed; produces optimized distroless images)

```groovy
// build.gradle
jib {
    from {
        image = 'eclipse-temurin:21-jre-alpine'
    }
    to {
        image = 'registry.example.com/api-gateway'
        tags = [project.version, 'latest']
    }
    container {
        jvmFlags = [
            '-Xms256m', '-Xmx384m',
            '-XX:+UseZGC',
            '-XX:MaxMetaspaceSize=128m',
            '-Djava.security.egd=file:/dev/./urandom',
            '-Dreactor.netty.pool.leasingStrategy=lifo'
        ]
        ports = ['8080', '9090']
        format = 'OCI'
    }
}
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
        - name: gateway
          image: registry.example.com/api-gateway:1.0.0
          ports:
            - containerPort: 8080
              name: http
            - containerPort: 9090
              name: admin
          envFrom:
            - configMapRef:
                name: gateway-config
            - secretRef:
                name: gateway-secrets
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 10
            timeoutSeconds: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
            timeoutSeconds: 3
          resources:
            requests:
              cpu: 500m
              memory: 384Mi
            limits:
              cpu: 1000m
              memory: 512Mi
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 15"]
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: api-gateway
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api-gateway
  minReplicas: 2
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
    - type: Pods
      pods:
        metric:
          name: gateway_requests_active
        target:
          type: AverageValue
          averageValue: 100
```

### Graceful Shutdown

- Spring Boot graceful shutdown: `server.shutdown=graceful`
- `spring.lifecycle.timeout-per-shutdown-phase=30s`
- PreStop hook: 15-second sleep to allow K8s to remove from Endpoints before SIGTERM
- Netty drains in-flight requests, rejects new ones during shutdown

### Resource Sizing Guide

| Environment | Replicas | CPU Request | Memory Request |
|-------------|----------|-------------|----------------|
| Dev | 1 | 250m | 256Mi |
| Staging | 2 | 500m | 384Mi |
| Production | 4 (min) | 500m | 384Mi |
| Production (peak) | 20 (HPA) | 1000m | 512Mi |

---

## 17. Folder Structure

```
api-gateway/
├── DESIGN.md                          # This document
├── AGENTS.md                          # Project context for AI coding agents
├── README.md                          # Quick start guide
├── build.gradle.kts                   # Gradle Kotlin DSL
├── settings.gradle.kts                # Module settings
├── gradle/
│   └── libs.versions.toml             # Version catalog
├── gradlew
├── gradlew.bat
├── docker/
│   └── Dockerfile.local               # Local dev Dockerfile (if not Jib)
├── k8s/
│   ├── base/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── configmap.yaml
│   │   └── hpa.yaml
│   └── overlays/
│       ├── dev/
│       ├── staging/
│       └── production/
├── scripts/
│   ├── start-local-infra.sh           # Redis + PostgreSQL via Docker Compose
│   └── docker-compose.yml
├── src/
│   ├── main/
│   │   ├── java/com/example/gateway/
│   │   │   ├── GatewayApplication.java
│   │   │   ├── config/
│   │   │   ├── route/
│   │   │   ├── security/
│   │   │   ├── ratelimit/
│   │   │   ├── resilience/
│   │   │   ├── filter/
│   │   │   ├── observability/
│   │   │   ├── admin/
│   │   │   ├── common/
│   │   │   └── model/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-staging.yml
│   │       ├── application-production.yml
│   │       └── logback-spring.xml
│   └── test/
│       ├── java/com/example/gateway/
│       │   ├── security/              # Unit tests for security filters
│       │   ├── ratelimit/             # Unit tests for rate limiter
│       │   ├── resilience/            # Unit tests for CB/retry/timeout
│       │   ├── route/                 # Unit tests for route matching
│       │   ├── filter/                # Unit tests for global filters
│       │   ├── integration/           # Integration tests (Testcontainers)
│       │   └── GatewayApplicationTests.java
│       └── resources/
│           ├── application-test.yml
│           └── wiremock/              # WireMock stubs
└── .github/
    └── workflows/
        ├── ci.yml                     # Build, test, lint
        └── cd.yml                     # Deploy to K8s
```

---

## 18. Coding Standards

### 18.1 General Principles

- **Reactive first**: Every filter, repository, and service returns `Mono<T>` or `Flux<T>`. No blocking calls.
- **Immutable where possible**: Use `record` for DTOs, `@Value` for config classes.
- **No nulls**: Use `Mono.empty()` or `Optional` instead of returning null.
- **Explicit dependencies**: Constructor injection + `final` fields. No field injection.
- **Small classes**: One filter factory per class. One responsibility per class.

### 18.2 Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Filter factories | `{Purpose}FilterFactory` | `JwtAuthenticationFilterFactory` |
| Config classes | Static inner `Config` class | `JwtAuthenticationFilterFactory.Config` |
| Properties | `{Domain}Properties` | `RateLimitProperties` |
| Repositories | `{Entity}Repository` | `ApiKeyRepository` |
| Validators | `{Subject}Validator` | `JwtTokenValidator` |
| Exceptions | `{Domain}Exception` | `AuthenticationException` |

### 18.3 Filter Factory Template

```java
@Component
public class ExampleFilterFactory
        extends AbstractGatewayFilterFactory<ExampleFilterFactory.Config> {

    public ExampleFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // ── Pre-filter ──
            String value = exchange.getRequest().getHeaders()
                    .getFirst(config.getHeaderName());

            // Continue chain
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                // ── Post-filter ──
                exchange.getResponse().getHeaders()
                        .add(config.getHeaderName(), "processed");
            }));
        };
    }

    @Data
    public static class Config {
        private String headerName = "X-Default";
        private boolean enabled = true;
    }

    // List of shortcut notation field names
    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("headerName", "enabled");
    }
}
```

### 18.4 Error Handling

```java
@Order(-1)
@Component
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = resolveStatus(ex);
        var body = new ErrorResponse(
            status.value(),
            status.getReasonPhrase(),
            ex.getMessage(),
            exchange.getAttribute(CORRELATION_ID_ATTR)
        );

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                    .bufferFactory()
                    .wrap(JsonUtils.toBytes(body))));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        return switch (ex) {
            case AuthenticationException e -> UNAUTHORIZED;
            case RateLimitExceededException e -> TOO_MANY_REQUESTS;
            case CircuitBreakerOpenException e -> SERVICE_UNAVAILABLE;
            case TimeoutException e -> GATEWAY_TIMEOUT;
            case NotFoundException e -> NOT_FOUND;
            default -> INTERNAL_SERVER_ERROR;
        };
    }
}
```

### 18.5 Reactor Best Practices

- Use `Mono.defer()` for lazy evaluation where needed
- Use `Hooks.onOperatorDebug()` only in dev (expensive in production)
- Prefer `.flatMap()` over nested `.map()` for async operations
- Always chain `.onErrorResume()`, `.onErrorMap()`, or `.doOnError()` after risky operations
- Use `Context` (Reactor) for request-scoped data (correlation ID, user info), not `ThreadLocal`
- Enable `Hooks.enableAutomaticContextPropagation()` for MDC propagation

### 18.6 Testing Naming

```java
class JwtTokenValidatorTest {
    @Test
    void shouldReturnPrincipalWhenTokenIsValid() {}
    @Test
    void shouldReturnEmptyWhenTokenIsExpired() {}
    @Test
    void shouldReturnErrorWhenSignatureIsInvalid() {}
    @Test
    void shouldThrowWhenJwksUnreachable() {}
}
```

---

## 19. Testing Strategy

### Test Pyramid

```
          ╱╲
         ╱  ╲
        ╱ E2E╲           ← 5%   (full gateway + upstream + Redis + PG)
       ╱──────╲
      ╱Integration╲      ← 20%  (filter + route + resil in context)
     ╱────────────╲
    ╱   Unit Tests  ╲    ← 75%  (individual classes, mocked deps)
   ╱────────────────╲
```

### 19.1 Unit Tests

| Layer | Framework | What to Test |
|-------|-----------|-------------|
| Filters | JUnit 5 + Mockito + StepVerifier | Each filter in isolation with mocked `ServerWebExchange` |
| Validators | JUnit 5 | JWT token parsing, API key hashing, CORS origin matching |
| Rate limiter | JUnit 5 + Mocked Redis | Token bucket logic, edge cases (first request, max capacity) |
| Config | JUnit 5 + `@SpringBootTest` | Configuration binding, validation |

**Example**:

```java
class JwtTokenValidatorTest {

    private final JwtTokenValidator validator = new JwtTokenValidator();

    @Test
    void shouldValidateTokenSuccessfully() {
        var token = generateTestToken(RS256, "user123", "USER");
        StepVerifier.create(validator.validate(token, validJwks()))
            .assertNext(principal -> {
                assertThat(principal.getName()).isEqualTo("user123");
                assertThat(principal.getRoles()).contains("USER");
            })
            .verifyComplete();
    }

    @Test
    void shouldRejectExpiredToken() {
        var token = generateExpiredToken();
        StepVerifier.create(validator.validate(token, validJwks()))
            .expectError(AuthenticationException.class)
            .verify();
    }
}
```

### 19.2 Integration Tests

**Infrastructure**: Testcontainers (Redis + PostgreSQL) + WireMock (upstream services)

```java
@SpringBootTest
@Testcontainers
class RateLimitingIntegrationTest {

    @Container
    static RedisContainer redis = new RedisContainer("redis:7-alpine")
        .withExposedPorts(6379);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + postgres.getHost() + ":" +
            postgres.getMappedPort(5432) + "/testdb");
    }

    @Autowired
    private TestWebClient client;

    @Test
    void shouldAllowRequestsWithinLimit() {
        Flux.range(1, 99)
            .flatMap(i -> client.get().uri("/api/v1/users")
                .header("X-API-Key", "valid-key")
                .exchange())
            .collectList()
            .as(StepVerifier::create)
            .assertNext(responses -> {
                assertThat(responses).allMatch(r ->
                    r.status().is2xxSuccessful());
            })
            .verifyComplete();
    }

    @Test
    void shouldRejectRequestsExceedingLimit() {
        Flux.range(1, 101)
            .flatMap(i -> client.get().uri("/api/v1/users")
                .header("X-API-Key", "valid-key")
                .exchange())
            .collectList()
            .as(StepVerifier::create)
            .assertNext(responses -> {
                long rejected = responses.stream()
                    .filter(r -> r.status().equals(HttpStatus.TOO_MANY_REQUESTS))
                    .count();
                assertThat(rejected).isGreaterThan(0);
            })
            .verifyComplete();
    }
}
```

**WireMock for upstream services**:

```java
@SpringBootTest
@WireMockTest(httpPort = 8081)
class CircuitBreakerIntegrationTest {

    @Test
    void shouldOpenCircuitAfterFailures() {
        // Arrange: WireMock returns 500 for first 6 calls
        stubFor(get("/api/v1/users")
            .willReturn(serverError()));

        // Act: make 6 requests
        Flux.range(1, 6)
            .flatMap(i -> client.get().uri("/api/v1/users").exchange())
            .blockLast();

        // Assert: 7th request gets 503 (circuit open)
        client.get().uri("/api/v1/users")
            .exchange()
            .expectStatus().is5xxServerError();
    }
}
```

### 19.3 End-to-End Tests

- Full stack: gateway → WireMock (upstreams) + Testcontainers (Redis, PG)
- Deployed in a real K8s environment (CI pipeline)
- Tools: Testcontainers + Bounday or REST Assured

### 19.4 Contract Tests

- Spring Cloud Contract for upstream service interfaces
- Verifies that route definitions match actual upstream API contracts

### 19.5 Performance Tests

- **Tool**: Gatling or k6
- **Scenarios**:
  - Baseline: 1000 req/s for 5 minutes (no auth, no rate limit)
  - Auth: 1000 req/s with JWT validation
  - Rate limit: 1000 req/s with rate limiter
  - Mixed: realistic traffic mix (80% auth, 10% rate-limited, 10% public)
- **Metrics**: p50, p95, p99 latency, error rate, CPU/memory usage
- **Goal**: Validate p99 < 50ms at 5000 req/s

### Test Infrastructure Script

```yaml
# scripts/docker-compose.yml
version: '3.8'
services:
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: apigateway
      POSTGRES_USER: gateway
      POSTGRES_PASSWORD: gateway
    ports: ["5432:5432"]
  otel-collector:
    image: otel/opentelemetry-collector:latest
    ports: ["4318:4318", "4317:4317"]
  prometheus:
    image: prom/prometheus:latest
    ports: ["9090:9090"]
```

---

## 20. Development Roadmap

### Phase 1: Foundation (Week 1–2)

| Task | Deliverable |
|------|-------------|
| Convert Maven → Gradle (Kotlin DSL) | `build.gradle.kts`, `settings.gradle.kts` |
| Version catalog (`libs.versions.toml`) | Dependency management |
| Spring Boot app scaffold | `GatewayApplication.java`, config classes |
| Basic YAML routing + RouteLocator | Static routes working |
| Correlation ID filter | `X-Correlation-ID` on every request |
| Structured JSON logging | Logstash encoder |
| Health endpoints | `/actuator/health`, `/actuator/info` |
| Docker image + K8s deployment | Jib build, deployment YAML |
| Docker Compose for local infra | Redis + PostgreSQL |
| Unit + integration test foundation | Testcontainers config |

**Gate**: Gateway proxies requests end-to-end with structured logging.

### Phase 2: Security (Week 3–4)

| Task | Deliverable |
|------|-------------|
| JWT validation filter | RS256/ES256, JWKS caching |
| API key validation filter | SHA-256 hash + PG lookup + Redis cache |
| Role-based route authorization | `requiredRoles` in route config |
| CORS per-route | `CorsConfiguration` per route metadata |
| Security headers (response filter) | HSTS, CSP, X-Frame-Options |
| Error handling framework | `GlobalErrorHandler`, typed exceptions |

**Gate**: Authenticated routes work with both JWT and API keys.

### Phase 3: Resilience (Week 5–6)

| Task | Deliverable |
|------|-------------|
| Rate limiting filter | Redis Lua token bucket |
| Rate limit key resolvers | USER, API_KEY, IP |
| Rate limit headers + 429 response | `Retry-After`, `X-RateLimit-*` |
| Circuit breaker filter | Resilience4j + fallback |
| Retry filter | Exponential backoff + jitter |
| Timeout filter | Per-route deadline |
| Feature flags | Toggle resilience patterns |

**Gate**: Gateway survives upstream failures gracefully.

### Phase 4: Observability (Week 7)

| Task | Deliverable |
|------|-------------|
| Micrometer metrics (custom) | Request count, latency, active |
| Prometheus endpoint | `/actuator/prometheus` |
| OpenTelemetry tracing | W3C TraceContext, span per filter |
| MDC context propagation | Correlation ID, trace ID in logs |
| Grafana dashboard | Request rate, latency, error rate, CB states |
| Admin endpoints | Route CRUD, cache flush, status |
| Tracing sampling configuration | Configurable rate per route |

**Gate**: All requests are logged, metered, and traced end-to-end.

### Phase 5: Dynamic Routes & Hardening (Week 8–9)

| Task | Deliverable |
|------|-------------|
| Redis RouteDefinitionRepository | Dynamic route CRUD |
| Redis Pub/Sub route refresh | Real-time route updates |
| Route validation at startup | Fail-fast on bad config |
| Rate limit + API key table in PG | Schema + reactive repository |
| Graceful shutdown configuration | Drain timeout, preStop hook |
| Performance tuning | Netty config, connection pooling, GC tuning |
| Security audit | OWASP scan, dependency check |
| Documentation | README, AGENTS.md, runbooks |

**Gate**: All features complete and documented.

### Phase 6: Testing & Polish (Week 10)

| Task | Deliverable |
|------|-------------|
| Comprehensive test suite | > 85% coverage |
| Performance test baseline | Gatling scenarios |
| Chaos testing | Kill pods, fail Redis, throttle upstreams |
| Load testing report | Verify latency/throughput SLAs |
| CI/CD pipeline | GitHub Actions (build, test, deploy) |
| Production runbook | Incident response, scaling guide |

**Gate**: Confident production release.

---

## Design Decisions & Trade-offs Summary

| Decision | Alternative Chosen | Rejected | Rationale |
|----------|--------------------|----------|-----------|
| Build tool | **Gradle** (Kotlin DSL) | Maven | Faster incremental builds, better multi-project support, modern |
| Project structure | **Single module, feature packages** | Multi-module, hexagonal | Keeps build simple; clear package boundaries suffice |
| Route source | **YAML + Redis overlay** | YAML-only, Redis-only | YAML for auditability, Redis for dynamism |
| Rate limiting | **Custom Lua-based token bucket** | SCG RequestRateLimiter | Full control over key resolution and response format |
| JWT library | **Nimbus JOSE + JWT** (via Spring Security) | jjwt, auth0 | Mature, well-maintained, Spring Security integrates with it |
| API key storage | **PostgreSQL + Redis cache** | All-in-memory, all-in-PG | Cache for performance, PG for durability |
| Circuit breaker | **Resilience4j** | Hystrix (EOL), Spring Retry | Only actively maintained choice for reactive apps |
| Tracing | **OpenTelemetry SDK** | Spring Cloud Sleuth (transitions to OTel) | Industry standard, vendor-neutral, future-proof |
| Metrics | **Micrometer** (bundled) | Dropwizard, custom | De facto standard for Spring Boot |
| Admin security | **mTLS + internal network** | JWT, basic auth | Admin is internal-only; mTLS is zero-overhead and robust |
| Error handling | **Custom ErrorWebExceptionHandler** | Default whitelabel, ControllerAdvice | Full control over error response format |

---

## Critical Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Redis becomes SPOF | Medium | High | Redis Sentinel / Cluster; fallback to local rate limiting |
| JWKS endpoint down at startup | Low | High | Cache JWKS to disk; use last-known-good cache on restart |
| Rate limit Lua script performance | Low | Medium | Benchmark with 10K req/s; Redis pipeline if needed |
| OTel exporter overloads gateway | Low | Medium | Batch span exporter; sampling rate < 20% in production |
| Memory leak in filter chain | Medium | High | Load test with steady state for 24h; monitor RSS |
| Netty connection pool exhaustion | Low | Medium | Configure connection pool per upstream; circuit breaker on pool timeout |
| Reactive stack debugging difficulty | High | Medium | `Hooks.onOperatorDebug()` in dev; structured logs with trace IDs |

---

## Key Libraries & Versions

| Dependency | Purpose |
|-----------|---------|
| `spring-boot-starter-webflux` | Reactive web server (Netty) |
| `spring-cloud-starter-gateway` | Route matching, filter chain, proxy |
| `spring-boot-starter-data-redis-reactive` | Redis connectivity (Lettuce) |
| `spring-boot-starter-actuator` | Health, metrics, info endpoints |
| `spring-boot-starter-security` | Reactive security context |
| `spring-cloud-circuitbreaker-resilience4j` | Circuit breaker + retry |
| `micrometer-registry-prometheus` | Prometheus metric export |
| `opentelemetry-api` + `opentelemetry-sdk` | Distributed tracing |
| `opentelemetry-exporter-otlp` | Trace export to OTel collector |
| `r2dbc-postgresql` | Reactive PostgreSQL driver |
| `nimbus-jose-jwt` | JWT parsing and validation |
| `logstash-logback-encoder` | Structured JSON logging |
| `testcontainers` + `wiremock` | Integration testing |

---

> **Next step**: Once this design is reviewed and approved, implementation will begin with Phase 1 (Foundation). The first concrete action is migrating `pom.xml` → `build.gradle.kts` and scaffolding the Spring Boot application with the package structure defined in Section 17.
