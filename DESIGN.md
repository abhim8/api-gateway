# API Gateway — Software Design Document (Revised V1)

> **Project**: API Gateway — front-door proxy for a microservices platform
> **Version**: 1.0 (Revised)
> **Status**: Approved for implementation

---

## 1. Vision and Goals

### Vision

Build a lightweight, secure, and observable API Gateway that acts as the single entry point for external traffic into the platform's microservices ecosystem — using **configuration over custom code** wherever possible.

### V1 Goals

| Goal | How |
|------|-----|
| **Security-first** | Validate every request with JWT via Spring Security OAuth2 Resource Server |
| **Resilient by default** | Circuit breaker, retry, and timeout via SCG built-in filter factories |
| **Observable** | Structured JSON logs, Micrometer metrics, OpenTelemetry traces |
| **Operable** | Health probes, graceful shutdown, Docker + Kubernetes ready |
| **Maintainable** | Minimal custom code (~6 classes); everything else is YAML config |

---

## 2. Scope and Non-goals

### V1 Scope

- HTTP(S) request routing to upstream services (static YAML routes)
- JWT validation via Spring Security OAuth2 Resource Server (JWKS, RS256/ES256)
- Correlation ID generation and propagation (`X-Correlation-ID`)
- Structured JSON logging with MDC context (correlation ID, trace ID)
- Micrometer metrics exposed via `/actuator/prometheus`
- OpenTelemetry distributed tracing (auto-instrumentation via Java agent)
- CORS (global and per-route)
- Retry on transient upstream failures (SCG `RetryGatewayFilterFactory`)
- Upstream call timeout (SCG route metadata `response-timeout`)
- Circuit breaker with fallback (SCG `CircuitBreakerGatewayFilterFactory`)
- Request/response header manipulation (SCG built-in filters)
- Health + readiness probes (`/actuator/health`)
- Docker image (Jib) + Kubernetes manifests
- Comprehensive test suite (unit + integration with WireMock)

### Explicitly V2+

| Feature | Reason |
|---------|--------|
| Dynamic route management (Redis) | Not needed until routes change without deploys |
| API key authentication | Requires PostgreSQL — justified as V2 add-on |
| Rate limiting | Requires Redis — significant infra dependency |
| Admin CRUD endpoints | Operational tooling, not core routing |
| PostgreSQL persistence | No feature in V1 needs it |
| Feature flags | Solve a problem that doesn't exist yet |
| Service discovery (Eureka/Consul) | K8s DNS is sufficient for V1 |
| Canary releases / Blue-green | Deployment strategy, not gateway feature |
| WebSocket / gRPC support | Out of V1 scope |
| GraphQL gateway | Terminated upstream, not at gateway |
| Traffic shadowing | V2 operational concern |
| Plugin framework | "Framework" is a red flag — wait for actual extension need |

---

## 3. Functional Requirements

| ID | Requirement | How |
|----|-------------|-----|
| FR-01 | Route requests based on path, method, and headers | SCG YAML routes + predicates |
| FR-02 | Validate JWT tokens on protected routes | Spring Security OAuth2 Resource Server |
| FR-03 | Apply circuit breaker per upstream route | SCG `CircuitBreakerGatewayFilterFactory` |
| FR-04 | Retry failed upstream requests on transient errors | SCG `RetryGatewayFilterFactory` |
| FR-05 | Enforce upstream request timeouts | Route metadata `response-timeout` |
| FR-06 | Add, remove, and transform HTTP headers | SCG `AddRequestHeader`, `RemoveRequestHeader`, etc. |
| FR-07 | Handle CORS preflight and origin validation | `spring.cloud.gateway.globalcors` |
| FR-08 | Generate and propagate correlation IDs | Custom `CorrelationIdFilterFactory` |
| FR-09 | Emit structured JSON logs per request | Logstash encoder + MDC |
| FR-10 | Expose health, readiness, and liveness endpoints | Spring Boot Actuator |
| FR-11 | Expose Prometheus metrics | Micrometer + `micrometer-registry-prometheus` |
| FR-12 | Distribute trace context to upstream services | OpenTelemetry auto-instrumentation |
| FR-13 | Reject unauthenticated requests with 401 | Spring Security authorization rules |
| FR-14 | Reject unauthorized requests with 403 | Spring Security role checks |
| FR-15 | Reject excessively large request bodies | `spring.codec.max-in-memory-size` |

---

## 4. Non-functional Requirements

| ID | Requirement | Target | How |
|----|-------------|--------|-----|
| NFR-01 | **Latency overhead** | p99 < 10ms | Minimal custom filters; reactive end-to-end |
| NFR-02 | **Throughput** | 3000+ req/s per pod (2 vCPU) | Netty event loop, no blocking |
| NFR-03 | **Availability** | 99.9% (3-nines) | Stateless + K8s rolling update + HPA |
| NFR-04 | **Startup time** | < 8 seconds | Spring Boot 4 optimizations, lazy init |
| NFR-05 | **Graceful shutdown** | Drain in-flight, zero dropped | `server.shutdown=graceful` + preStop hook |
| NFR-06 | **Security** | OWASP Top 10 | JWT validation, secure headers, input size limits |
| NFR-07 | **Observability** | Every request logged + metered | Correlation ID + Micrometer + traces |
| NFR-08 | **Resource limits** | CPU < 1.0 / mem < 384 MiB | Predictable cost profile |
| NFR-09 | **Test coverage** | > 85% lines | Unit + integration tests with WireMock |

---

## 5. High-level Architecture

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
            │  Spring Cloud Gateway 4.x       │
            │  Netty / WebFlux / Reactor      │
            │  Java 21                        │
            │                                 │
            │  ┌─── Filter Pipeline ───────┐  │
            │  │ CORS → Correlation →      │  │
            │  │ Spring Security (JWT) →   │  │
            │  │ Route Match → Resilience  │  │
            │  │ → Proxy → Response        │  │
            │  └───────────────────────────┘  │
            └────┬────────┬────────┬──────────┘
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

### Architecture Tenets

1. **Stateless**: Zero local state. No Redis, no database. Every pod is identical.
2. **Configuration over code**: SCG built-in filters handle 80% of requirements. Custom code is the exception.
3. **Reactive end-to-end**: Netty event loop does all work — no thread pool blocking.
4. **Fail-fast**: Invalid JWT, missing auth, or bad requests are rejected immediately — no upstream round-trip.
5. **Defend upstreams**: Circuit breakers, retries, and timeouts protect backend services from cascading failure.

---

## 6. Component Diagram

```
┌──────────────────────────────────────────────────────────┐
│                    API Gateway (Pod)                      │
│                                                           │
│  ┌──────────────────────────────────────────────────┐    │
│  │              Spring Security Chain                │    │
│  │  ┌────────────────────────────────────────────┐  │    │
│  │  │  OAuth2 Resource Server (JWT validation)    │  │    │
│  │  │  - JWKS fetching (auto, cached)             │  │    │
│  │  │  - Token parsing (Nimbus)                   │  │    │
│  │  │  - Principal extraction                      │  │    │
│  │  └────────────────────────────────────────────┘  │    │
│  └──────────────────────────────────────────────────┘    │
│                                                           │
│  ┌──────────────────────────────────────────────────┐    │
│  │           SCG Filter Pipeline                    │    │
│  │                                                  │    │
│  │  Pre-filters:                                    │    │
│  │    ├─ CorsGlobalFilter              (built-in)   │    │
│  │    └─ CorrelationIdFilterFactory    (custom)     │    │
│  │                                                  │    │
│  │  Route filters: (per-route YAML)                 │    │
│  │    ├─ RetryGatewayFilterFactory     (built-in)   │    │
│  │    ├─ CircuitBreakerFilterFactory  (built-in)    │    │
│  │    ├─ AddRequestHeader             (built-in)    │    │
│  │    ├─ RemoveResponseHeader         (built-in)    │    │
│  │    └─ PrefixPath / RewritePath     (built-in)    │    │
│  │                                                  │    │
│  │  Post-filters:                                   │    │
│  │    └─ (response handled by SCG)                  │    │
│  └──────────────────────────────────────────────────┘    │
│                                                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │Actuator  │  │Micrometer│  │ OTel     │               │
│  │/health   │  │/prometheus│ │ Agent    │               │
│  │/info     │  │          │  │(traces)  │               │
│  └──────────┘  └──────────┘  └──────────┘               │
└──────────────────────────────────────────────────────────┘
```

### Components

| Component | Type | Responsibility |
|-----------|------|----------------|
| `SecurityWebFilterChain` | Spring Security | JWT validation, path authorization, CORS |
| `RouteLocator` | SCG built-in | Route matching from YAML definitions |
| `CorrelationIdFilterFactory` | Custom `GatewayFilter` | Generate/propagate `X-Correlation-ID` |
| `RetryGatewayFilterFactory` | SCG built-in | Retry on 5xx with exponential backoff |
| `CircuitBreakerGatewayFilterFactory` | SCG built-in | Open circuit on failure threshold |
| `FallbackController` | Custom `@Controller` | Return structured 503 on circuit open |
| `Header manipulation filters` | SCG built-in | Add/remove/rewrite request and response headers |
| `CorsGlobalFilter` | SCG built-in | Validate origins, handle preflight |
| `Actuator endpoints` | Spring Boot | Health, metrics, info |
| OTEL Java agent | External | Auto-instrument HTTP client, Netty |

---

## 7. Request Lifecycle

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
  │                   │ 4. Spring    │                       │
  │                   │ Security     │                       │
  │                   │ JWT validate │                       │
  │                   │ role check   │                       │
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

### Phase Details

| Phase | Mechanism | Error Response |
|-------|-----------|----------------|
| 1 | Netty HTTP parser | 400 (malformed request) |
| 2 | `CorsGlobalFilter` | 403 (origin denied) |
| 3 | `CorrelationIdFilterFactory` | — |
| 4 | Spring Security `SecurityWebFilterChain` | 401 (no/invalid JWT), 403 (insufficient role) |
| 5 | SCG `RouteLocator` | 404 (no matching route) |
| 6 | SCG `RetryGatewayFilterFactory` | Retries transparently; 502 if all fail |
| 7 | SCG `CircuitBreakerGatewayFilterFactory` | 503 + fallback response |
| 8 | Netty `HttpClient` with `response-timeout` | 504 (upstream timeout) |
| 9 | SCG post-filters + `GlobalErrorHandler` | — |

---

## 8. Filter Pipeline

### Filter Order

```
Order   Filter                          Source        Type
──────  ────────────────────────────    ──────────    ──────────────
-200    CorsGlobalFilter                SCG built-in  Global (all routes)
-100    CorrelationIdFilterFactory      Custom        Global (all routes)
  -1    SecurityWebFilterChain          Spring Sec    Security filter
  +0    Route predicates + per-route    SCG + YAML    Per-route
        filters (Retry, CB, headers)
 +100   GlobalErrorHandler              Custom        Error handler (post)
```

### V1 Filter Inventory

#### Global (apply to every request)

| Filter | Implementation | Notes |
|--------|---------------|-------|
| CORS | `spring.cloud.gateway.globalcors` | YAML config only |
| Correlation ID | `CorrelationIdFilterFactory` | Only custom global filter |
| JWT Auth | Spring Security OAuth2 RS | `SecurityConfig.java` |
| Metrics | Micrometer auto-config | No code needed |
| Tracing | OTel Java agent auto-instrumentation | No code needed |
| Error handling | `GlobalErrorHandler` | Custom `ErrorWebExceptionHandler` |

#### Per-route (configurable in YAML)

| Filter | SCG Factory | Config Example |
|--------|-------------|----------------|
| Retry | `RetryGatewayFilterFactory` | `- Retry=retries:3,series:SERVER_ERROR` |
| Circuit Breaker | `CircuitBreakerGatewayFilterFactory` | `- CircuitBreaker=name:myCB,fallbackUri:forward:/fallback` |
| Add header | `AddRequestHeaderGatewayFilterFactory` | `- AddRequestHeader=X-Proxy:true` |
| Remove header | `RemoveRequestHeaderGatewayFilterFactory` | `- RemoveRequestHeader=X-Internal` |
| Rewrite path | `RewritePathGatewayFilterFactory` | `- RewritePath=/api/v1/users/(.*), /$\{segment1}` |
| Strip prefix | `StripPrefixGatewayFilterFactory` | `- StripPrefix=1` |

---

## 9. Module and Package Structure

### Single Maven module

```
gateway
├── GatewayApplication.java          # @SpringBootApplication
├── config/
│   └── SecurityConfig.java          # OAuth2 RS + CORS + authorization rules
├── filter/
│   └── CorrelationIdFilterFactory.java  # X-Correlation-ID global filter
├── web/
│   └── FallbackController.java      # Circuit breaker fallback endpoint
└── common/
    ├── exception/
    │   └── GlobalErrorHandler.java  # Structured error responses
    └── util/
        └── HeaderConstants.java     # Header name constants
```

**Total custom classes: 6**

### Package Dependency Rules

```
config → (none — Spring Security auto-config)
filter → common
web → (none)
common → (none)
```

No circular dependencies — each package is independently testable.

---

## 10. Routing Engine Design

### Route Definition (entirely YAML)

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "https://app.example.com"
            allowedMethods: GET,POST,PUT,DELETE,PATCH,OPTIONS
            allowedHeaders: "*"
            maxAge: 1800

      routes:
        # Public health route (no auth)
        - id: health
          uri: http://health-service:8080
          predicates:
            - Path=/health/**
          filters:
            - StripPrefix=1

        # Protected API — users
        - id: users-api
          uri: http://user-service:8080
          predicates:
            - Path=/api/v1/users/**
            - Method=GET,POST,PUT,DELETE
          filters:
            - name: Retry
              args:
                retries: 3
                series: SERVER_ERROR
                methods: GET
                statuses: 500,502,503
            - name: CircuitBreaker
              args:
                name: usersCircuitBreaker
                fallbackUri: forward:/fallback/users
            - StripPrefix=1
            - AddRequestHeader=X-Gateway-Proxy: true
            - RemoveResponseHeader=X-Powered-By
          metadata:
            response-timeout: 5000

        # Protected API — orders
        - id: orders-api
          uri: http://order-service:8080
          predicates:
            - Path=/api/v1/orders/**
          filters:
            - name: Retry
              args:
                retries: 2
                series: SERVER_ERROR
            - name: CircuitBreaker
              args:
                name: ordersCircuitBreaker
                fallbackUri: forward:/fallback/orders
            - StripPrefix=1
          metadata:
            response-timeout: 10000
```

### Route Resolution

```
1. Request enters → Netty reads HTTP headers
2. CORS validation (globalcors)
3. Correlation ID filter adds X-Correlation-ID
4. Spring Security checks JWT for protected paths
5. SCG RouteLocator evaluates each route's predicates in order
6. First matching route: combine global + route filters
7. No match: 404
```

**No custom route resolution code.** SCG's built-in `RouteLocator` with YAML definitions handles everything.

### Timeout Configuration

```yaml
# Per-route timeout (in route metadata):
metadata:
  response-timeout: 5000

# Global default timeout:
spring:
  cloud:
    gateway:
      httpclient:
        response-timeout: 5s
```

---

## 11. Security Design

### JWT Authentication

Spring Security OAuth2 Resource Server handles everything:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com
          jwk-set-uri: https://auth.example.com/.well-known/jwks.json
```

**What this gives us for free:**
- JWKS fetching and caching (auto, configurable TTL)
- JWT signature validation (RS256, ES256, etc.)
- Expiration, issuer, audience validation
- Principal extraction to `exchange.getPrincipal()`
- Reactive `ReactiveJwtDecoder` — no blocking

### Security Rules

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                // Public routes
                .pathMatchers(GET, "/actuator/health/**").permitAll()
                .pathMatchers(GET, "/actuator/info").permitAll()
                .pathMatchers("/fallback/**").permitAll()

                // Protected routes — require authentication
                .pathMatchers("/api/v1/**").authenticated()

                // Admin routes — require specific role
                .pathMatchers("/actuator/**").hasRole("ADMIN")

                // Everything else requires auth
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
            )
            .build();
    }
}
```

### CORS

Configured at the SCG level, not Spring Security. Preflight OPTIONS never hits auth:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "${GATEWAY_CORS_ORIGINS:https://app.example.com}"
            allowedMethods: GET,POST,PUT,DELETE,PATCH,OPTIONS
            allowedHeaders: Authorization,Content-Type,X-Correlation-ID
            allowCredentials: true
            maxAge: 1800
```

### Security Headers (auto-added by Spring Security)

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Strict-Transport-Security: max-age=31536000; includeSubDomains
Cache-Control: no-store
```

---

## 12–13. Resilience Design

### Circuit Breaker

Uses SCG's `CircuitBreakerGatewayFilterFactory` backed by Resilience4j.

```yaml
# In route filters:
- name: CircuitBreaker
  args:
    name: usersCircuitBreaker
    fallbackUri: forward:/fallback/users

# In application.yml:
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
    instances:
      usersCircuitBreaker:
        baseConfig: default
```

### Retry

Uses SCG's `RetryGatewayFilterFactory`:

```yaml
- name: Retry
  args:
    retries: 3
    series: SERVER_ERROR
    methods: GET
    statuses: 500,502,503
```

Rules: retry GET only (idempotent), max 3 attempts, exponential backoff (auto).

### Timeout

Per-route via route metadata:

```yaml
metadata:
  response-timeout: 5000  # milliseconds
```

Global default in `application.yml`:

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        response-timeout: 5s
```

### Fallback Controller

```java
@RestController
public class FallbackController {

    @GetMapping("/fallback/{routeId}")
    public Mono<Map<String, Object>> fallback(@PathVariable String routeId,
                                               ServerWebExchange exchange) {
        return Mono.just(Map.of(
            "status", 503,
            "error", "Service temporarily unavailable",
            "route", routeId,
            "correlationId", exchange.getAttribute("correlationId")
        ));
    }
}
```

### Resilience Strategy Summary

| Pattern | Trigger | Response | Code |
|---------|---------|----------|------|
| Circuit Breaker | 5 failures in 10-call window | 503 + fallback | YAML only |
| Retry | 5xx response | Transparent retry (max 3) | YAML only |
| Timeout | No response within `response-timeout` | 504 | YAML only |

---

## 14. Observability Design

### 14.1 Structured Logging

**logback-spring.xml:**

```xml
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>

    <logger name="gateway" level="DEBUG"/>
</configuration>
```

**MDC context** populated by `CorrelationIdFilterFactory`:

```java
return chain.filter(exchange).contextWrite(ctx ->
    ctx.put("correlationId", correlationId));
```

`Hooks.enableAutomaticContextPropagation()` in `GatewayApplication.java` ensures MDC propagates through Reactor.

### 14.2 Metrics

Micrometer auto-configures with `micrometer-registry-prometheus`:

| Metric | Source | Type |
|--------|--------|------|
| `http.server.requests` | Spring WebFlux auto | Timer (method, status, uri) |
| `resilience4j.circuitbreaker.calls` | Resilience4j auto | Counter |
| `resilience4j.circuitbreaker.state` | Resilience4j auto | Gauge |
| `jvm.*` | JVM Micrometer | Various |

No custom metrics code needed.

### 14.3 Distributed Tracing

**OpenTelemetry Java agent** is attached at runtime via JVM args:

```bash
-javaagent:opentelemetry-javaagent.jar
-Dotel.service.name=api-gateway
-Dotel.exporter.otlp.endpoint=http://otel-collector:4318
-Dotel.traces.sampler=parentbased_always_on
-Dotel.propagators=tracecontext,baggage
```

This auto-instruments:
- Netty HTTP server (incoming requests)
- Netty HTTP client (outgoing proxy calls)
- Adds trace context headers (`traceparent`) to upstream requests

**Zero custom tracing code.** No manual span creation in V1.

### 14.4 Health Endpoints

| Endpoint | Purpose | Probes |
|----------|---------|--------|
| `/actuator/health` | Health + readiness | Liveness: `/actuator/health/liveness` |
| `/actuator/health/liveness` | Is the process alive? | Always UP |
| `/actuator/health/readiness` | Can it serve traffic? | Always UP (no external deps) |
| `/actuator/info` | Build info, git commit | Build metadata |
| `/actuator/prometheus` | Metrics scrape target | Prometheus |

No custom health indicators — gateway has no external dependencies to check. `application.yml` configures separate probe paths.

---

## 15. Configuration Model

### Configuration Sources (by precedence)

```
1. Environment variables / K8s secrets    (highest)
2. application-{profile}.yml               (profile-specific)
3. application.yml                         (base)
```

### Application YAML Structure

```yaml
server:
  port: 8080
  shutdown: graceful
  netty:
    connection-timeout: 5s

spring:
  application:
    name: api-gateway
  lifecycle:
    timeout-per-shutdown-phase: 30s
  codec:
    max-in-memory-size: 256KB

  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: ${GATEWAY_CORS_ORIGINS:https://app.example.com}
            allowedMethods: GET,POST,PUT,DELETE,PATCH,OPTIONS
            allowedHeaders: Authorization,Content-Type,X-Correlation-ID
            allowCredentials: true
            maxAge: 1800
      httpclient:
        response-timeout: 5s
        connect-timeout: 3s
        pool:
          type: ELASTIC
          max-connections: 500
          max-idle-time: 60s
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

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI:https://auth.example.com}
          jwk-set-uri: ${JWKS_URI:https://auth.example.com/.well-known/jwks.json}

resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
      base-path: /actuator
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  metrics:
    tags:
      application: ${spring.application.name}

logging:
  level:
    root: INFO
    gateway: DEBUG
```

No custom `@ConfigurationProperties` classes — everything uses Spring Boot's native configuration keys.

---

## 16. Deployment Architecture

### Docker Image

**Jib Maven plugin** builds optimized OCI image:

```xml
<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <configuration>
        <from>
            <image>eclipse-temurin:21-jre-alpine</image>
        </from>
        <to>
            <image>registry.example.com/api-gateway</image>
            <tags>
                <tag>${project.version}</tag>
                <tag>latest</tag>
            </tags>
        </to>
        <container>
            <jvmFlags>
                <jvmFlag>-Xms256m</jvmFlag>
                <jvmFlag>-Xmx256m</jvmFlag>
                <jvmFlag>-XX:+UseZGC</jvmFlag>
                <jvmFlag>-XX:MaxMetaspaceSize=128m</jvmFlag>
                <jvmFlag>-Djava.security.egd=file:/dev/./urandom</jvmFlag>
            </jvmFlags>
            <ports>
                <port>8080</port>
            </ports>
            <format>OCI</format>
        </container>
    </configuration>
</plugin>
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  replicas: 2
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
          envFrom:
            - configMapRef: { name: gateway-config }
            - secretRef: { name: gateway-secrets }
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 10
            timeoutSeconds: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 5
            timeoutSeconds: 3
          resources:
            requests:
              cpu: 250m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 384Mi
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
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
```

### Graceful Shutdown

- `server.shutdown=graceful` — Spring Boot drains in-flight requests
- `spring.lifecycle.timeout-per-shutdown-phase=30s` — max drain window
- PreStop hook (15s sleep) — gives K8s time to remove pod from Service endpoints before SIGTERM

---

## 17. Folder Structure

```
api-gateway/
├── DESIGN.md                          # This document
├── REVIEW.md                          # Architecture review of v1 draft
├── ROADMAP.md                         # Implementation milestones
├── AGENTS.md                          # AI coding agent context
├── README.md                          # Quick start
├── pom.xml                            # Maven build
├── mvnw / mvnw.cmd
├── scripts/
│   └── docker-compose.yml             # Local upstreams (WireMock)
├── k8s/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── configmap.yaml
│   └── hpa.yaml
├── src/
│   ├── main/
│   │   ├── java/gateway/
│   │   │   ├── GatewayApplication.java
│   │   │   ├── config/
│   │   │   │   └── SecurityConfig.java
│   │   │   ├── filter/
│   │   │   │   └── CorrelationIdFilterFactory.java
│   │   │   ├── web/
│   │   │   │   └── FallbackController.java
│   │   │   └── common/
│   │   │       ├── exception/
│   │   │       │   └── GlobalErrorHandler.java
│   │   │       └── util/
│   │   │           └── HeaderConstants.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── logback-spring.xml
│   └── test/
│       ├── java/gateway/
│       │   ├── filter/
│       │   │   └── CorrelationIdFilterFactoryTest.java
│       │   ├── web/
│       │   │   └── FallbackControllerTest.java
│       │   └── integration/
│       │       └── GatewayIntegrationTest.java
│       └── resources/
│           ├── application-test.yml
│           └── wiremock/
│               └── __files/
│                   └── users.json
└── .github/workflows/
    └── ci.yml
```

---

## 18. Coding Standards

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
| Filter factories | `{Purpose}FilterFactory` | `CorrelationIdFilterFactory` |
| Controllers | `{Resource}Controller` | `FallbackController` |
| Config classes | `{Domain}Config` | `SecurityConfig` |
| Error handler | `Global{Type}Handler` | `GlobalErrorHandler` |
| Test classes | `{ClassUnderTest}Test` | `CorrelationIdFilterFactoryTest` |

### Filter Factory Template

```java
@Component
public class CorrelationIdFilterFactory
        extends AbstractGatewayFilterFactory<CorrelationIdFilterFactory.Config> {

    public CorrelationIdFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Pre-filter: ensure correlation ID exists
            ...
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                // Post-filter: add to response
                ...
            }));
        };
    }

    public record Config(boolean enabled) {}
}
```

### Error Handling

```java
@Order(-1)
@Component
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        var status = resolveStatus(ex);
        var body = new ErrorResponse(
            status.value(),
            status.getReasonPhrase(),
            exchange.getAttribute(CORRELATION_ID)
        );
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders()
            .setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse()
            .writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(serialize(body))));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        return ex instanceof ResponseStatusException rse
            ? HttpStatus.valueOf(rse.getBody().getStatus())
            : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
```

### Reactor Best Practices

- `Hooks.enableAutomaticContextPropagation()` in main class for MDC
- `.flatMap()` for async, `.map()` for sync — never nest
- `.onErrorResume()` or `.onErrorMap()` on every risky chain
- Use Reactor `Context` for request-scoped data, not `ThreadLocal`
- `Mono.defer()` for lazy evaluation

---

## 19. Testing Strategy

### Test Pyramid

```
          ╱╲
         ╱  ╲
        ╱ E2E╲           ← 10%  (full integration with WireMock)
       ╱──────╲
      ╱Integration╲      ← 30%  (filter + controller in Spring context)
     ╱────────────╲
    ╱   Unit Tests  ╲    ← 60%  (individual classes in isolation)
   ╱────────────────╲
```

### 19.1 Unit Tests

| Class | Framework | What to Test |
|-------|-----------|-------------|
| `CorrelationIdFilterFactory` | JUnit 5 + Mockito + StepVerifier | ID generation, propagation, missing ID handling |
| `FallbackController` | JUnit 5 + Mockito | Response shape, correlation ID in response |
| `GlobalErrorHandler` | JUnit 5 + Mockito | Status mapping, JSON body format |

### 19.2 Integration Tests

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayIntegrationTest {

    @Autowired
    private WebTestClient webClient;

    @Test
    void shouldReturn401WhenNoJwtProvided() {
        webClient.get().uri("/api/v1/users")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturn200WhenValidJwtProvided() {
        webClient.get().uri("/api/v1/users")
            .header("Authorization", "Bearer " + validJwt())
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void shouldReturnCorrelationIdInResponse() {
        webClient.get().uri("/api/v1/users")
            .header("Authorization", "Bearer " + validJwt())
            .exchange()
            .expectHeader().exists("X-Correlation-ID");
    }

    @Test
    void shouldReturn503WhenCircuitBreakerOpen() {
        // Trigger circuit break via repeated 5xx from WireMock
        ...
        webClient.get().uri("/api/v1/orders")
            .header("Authorization", "Bearer " + validJwt())
            .exchange()
            .expectStatus().is5xxServerError();
    }
}
```

### 19.3 WireMock Upstreams

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@WireMockTest(httpPort = 8081)
class RoutingIntegrationTest {

    @Test
    void shouldRouteToUpstream() {
        stubFor(get("/api/v1/users")
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{"users":[]}")));

        webClient.get().uri("/api/v1/users")
            .header("Authorization", "Bearer " + validJwt())
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("users").isArray();
    }
}
```

### 19.4 Test Configuration

```yaml
# src/test/resources/application-test.yml
spring:
  cloud:
    gateway:
      routes:
        - id: test-route
          uri: http://localhost:${wiremock.server.port:8081}
          predicates:
            - Path=/api/v1/**
          filters:
            - StripPrefix=1
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: classpath:test-jwks.json
```

### What We DON'T Need in V1

| Tool | Reason |
|------|--------|
| Testcontainers | No Redis, PostgreSQL, or external services |
| REST Assured | WebTestClient from Spring is superior for reactive |
| Gatling / k6 | Performance testing is V2 |
| Spring Cloud Contract | Overkill until multiple teams own upstreams |

---

## 20. Development Roadmap

| Phase | Scope | Duration | Est. Classes |
|-------|-------|----------|-------------|
| **M1** | Scaffold + build + basic routing | ~2-3 days | 2 |
| **M2** | Correlation ID + structured logging | ~1-2 days | 2 |
| **M3** | JWT authentication + security config | ~2-3 days | 2 |
| **M4** | Resilience (CB + retry + timeout + fallback) | ~2-3 days | 1 |
| **M5** | Metrics + tracing + health endpoints | ~1-2 days | 0 |
| **M6** | Docker + K8s deployment | ~2-3 days | 0 |
| **M7** | Comprehensive testing + production hardening | ~2-3 days | 0 |

**Total:** ~14-19 days. ~6 custom classes. The rest is YAML configuration.

---

## Libraries & Dependencies

### Runtime

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `spring-boot-starter-webflux` | 4.x | Reactive web server (Netty) |
| `spring-cloud-starter-gateway` | 5.x | Routing, predicates, filter chain |
| `spring-boot-starter-actuator` | 4.x | Health, metrics, info endpoints |
| `spring-boot-starter-oauth2-resource-server` | 4.x | JWT validation via OAuth2 RS |
| `spring-cloud-starter-circuitbreaker-reactor-resilience4j` | 4.x | Circuit breaker |
| `micrometer-registry-prometheus` | 1.x | Prometheus metric export |
| `net.logstash.logback:logstash-logback-encoder` | 8.x | Structured JSON logging |
| `opentelemetry-javaagent` | 2.x | Auto-instrumentation for tracing |

### Test

| Dependency | Purpose |
|-----------|---------|
| `spring-boot-starter-test` | JUnit 5, Mockito |
| `io.projectreactor:reactor-test` | StepVerifier |
| `org.wiremock:wiremock-standalone` | HTTP stub for upstreams |
| `org.springframework.security:spring-security-test` | JWT test helpers |
