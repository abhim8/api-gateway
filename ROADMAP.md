# API Gateway — Implementation Roadmap

> **Target**: V1 with static YAML routing, JWT auth, resilience, observability, Docker + K8s  
> **Total custom classes**: 6  
> **Estimated effort**: 14–19 days  
> **Guiding principle**: Each milestone is independently testable and production-ready.

---

## Milestone 1: Foundation — Project Scaffold + Build + Basic Routing

**Objective**: Set up the Maven project, Spring Boot application, and YAML-based routing that proxies requests to upstream services.

### Files to Create

| File | Purpose |
|------|---------|
| `pom.xml` | Maven build with all dependencies and plugins |
| `mvnw` / `mvnw.cmd` | Maven wrapper |
| `src/main/java/gateway/GatewayApplication.java` | `@SpringBootApplication` entry point |
| `src/main/resources/application.yml` | SCG routes, server config, logging |
| `src/test/resources/application-test.yml` | Test profile with routes pointing to WireMock |
| `src/test/java/gateway/GatewayApplicationTests.java` | Context load + basic route test |

### Classes to Implement

```java
// GatewayApplication.java — 3 lines
@SpringBootApplication
@EnableHooks(enableAutomaticContextPropagation = true)
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

### Tests to Write

```java
// GatewayApplicationTests.java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayApplicationTests {

    @Autowired
    private WebTestClient webClient;

    @Test
    void contextLoads() {
        // Verifies application starts without errors
    }

    @Test
    void shouldReturn404ForUnknownRoute() {
        webClient.get().uri("/nonexistent")
            .exchange()
            .expectStatus().isNotFound();
    }
}
```

### Acceptance Criteria

- [x] `./mvnw clean verify` succeeds
- [x] Application starts on port 8000
- [x] Requests to unknown routes return 404
- [x] Requests to configured routes return 200 (pointed at test upstream)
- [x] Graceful shutdown works (`SIGTERM` → in-flight requests complete)
- [x] All tests pass (`./mvnw test`)

---

## Milestone 2: Correlation ID + Structured Logging

**Objective**: Every request gets a unique `X-Correlation-ID`, propagated to upstream services and included in structured JSON logs.

### Files to Create

| File | Purpose |
|------|---------|
| `src/main/java/gateway/filter/CorrelationIdGlobalFilter.java` | Global `GlobalFilter` that generates/forwards correlation ID |
| `src/main/java/gateway/common/util/HeaderConstants.java` | Shared header name constants |
| `src/main/resources/log4j2.xml` | Log4j2 JSON console logging |
| `src/main/resources/log-layout.json` | JsonTemplateLayout field definitions |
| `src/test/java/gateway/filter/CorrelationIdGlobalFilterTest.java` | Unit tests |

### Classes to Implement

| Class | Responsibility |
|-------|----------------|
| `CorrelationIdGlobalFilter` | Generate `X-Correlation-ID` if absent; add to request headers, response headers, Reactor Context, and MDC |
| `HeaderConstants` | `String` constants for `X-Correlation-ID`, `X-Request-Id`, etc. |

### Tests to Write

**CorrelationIdGlobalFilterTest.java** (unit):

| Test | Scenario |
|------|----------|
| `shouldGenerateIdWhenNotPresent` | No correlation ID in request → UUID generated |
| `shouldForwardExistingId` | Existing `X-Correlation-ID` → forwarded without modification |
| `shouldAddIdToResponseHeaders` | Correlation ID appears in response headers |
| `shouldSetMdcContext` | Correlation ID available in Reactor Context |

### Acceptance Criteria

- [x] Every response includes `X-Correlation-ID` header
- [x] If client sends `X-Correlation-ID`, it is preserved
- [x] Upstream proxy request includes `X-Correlation-ID` header
- [x] Logs are valid JSON (Log4j2 JsonTemplateLayout format)
- [x] Logs include `correlation_id` field matching the header
- [x] Logs include `trace_id` and `span_id` when OTel agent is attached
- [x] Tests pass

---

## Milestone 3: JWT Authentication + CORS + Security Headers

**Objective**: Protect API routes with JWT validation via Spring Security OAuth2 Resource Server. Configure CORS and security headers.

### Files to Create

| File | Purpose |
|------|---------|
| `src/main/java/gateway/config/SecurityConfig.java` | OAuth2 RS + auth rules + CORS |
| `src/test/java/gateway/integration/GatewayIntegrationTest.java` | Auth + CORS + routing integration tests |
| `src/test/resources/test-jwks.json` | JWKS fixture for testing |
| `src/test/resources/application-test.yml` | (Update) test routes + JWKS config |

### Classes to Implement

| Class | Responsibility |
|-------|----------------|
| `SecurityConfig` | `SecurityWebFilterChain` with OAuth2 RS, path authorization, CORS |

### Tests to Write

**GatewayIntegrationTest.java** (integration):

| Test | Scenario |
|------|----------|
| `shouldReturn401WhenNoJwt` | No auth header → 401 |
| `shouldReturn401WhenMalformedJwt` | Invalid token → 401 |
| `shouldReturn200WhenValidJwt` | Valid RS256 JWT → 200 |
| `shouldReturn403WhenInsufficientRole` | Valid JWT, wrong role → 403 |
| `shouldPermitPublicRoutes` | `/actuator/health` → 200 (no auth) |
| `shouldReturn200ForCorsPreflight` | `OPTIONS` with valid origin → 200 + CORS headers |
| `shouldReturn403ForCorsInvalidOrigin` | `OPTIONS` with blocked origin → 403 |
| `shouldIncludeSecurityHeaders` | Response includes `X-Content-Type-Options`, `Strict-Transport-Security` |

### YAML Config

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}
          jwk-set-uri: ${JWKS_URI}
```

### Acceptance Criteria

- [x] Protected routes (`/api/v1/**`) return 401 without valid JWT
- [x] Protected routes return 200 with valid JWT
- [x] Public routes (`/actuator/health`, `/fallback/**`) work without auth
- [x] CORS preflight responses include correct headers
- [x] Every response includes security headers
- [x] JWKS caching works (no repetitive network calls)
- [x] Tests pass

---

## Milestone 4: Resilience — Circuit Breaker + Retry + Timeout + Fallback

**Objective**: Protect upstream services from cascading failures using SCG's built-in resilience filters.

### Files to Create

| File | Purpose |
|------|---------|
| `src/main/java/gateway/web/FallbackController.java` | CB fallback response handler |
| `src/test/java/gateway/web/FallbackControllerTest.java` | Unit tests for fallback |
| `src/test/resources/wiremock/__files/users.json` | Sample upstream response |

### Classes to Implement

| Class | Responsibility |
|-------|----------------|
| `FallbackController` | `@RestController` returning structured 503 with correlation ID |

### Existing Files to Modify

| File | Change |
|------|--------|
| `src/main/resources/application.yml` | Add Resilience4j config, add `Retry`/`CircuitBreaker`/`response-timeout` to route filters |

### Tests to Write

**FallbackControllerTest.java** (unit):

| Test | Scenario |
|------|----------|
| `shouldReturn503WithCorrelationId` | Fallback response includes `status: 503` and correlation ID |

**GatewayIntegrationTest.java** (integration, add these):

| Test | Scenario |
|------|----------|
| `shouldRetryOnServerError` | Upstream returns 500 → retry occurs (at least 2 requests seen by WireMock) |
| `shouldOpenCircuitBreakerOnFailures` | Repeated 5xx → CB opens → 503 returned |
| `shouldCloseCircuitAfterRecovery` | CB open → wait + successful request → CB closes |
| `shouldReturn504OnTimeout` | Upstream hangs → 504 after `response-timeout` |
| `shouldNotRetryOn4xx` | Upstream returns 400 → no retry |
| `shouldNotRetryOnPost` | Non-idempotent method → no retry |

### YAML Config Added

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

spring:
  cloud:
    gateway:
      routes:
        - id: users-api
          filters:
            - name: Retry
              args:
                retries: 3
                series: SERVER_ERROR
                methods: GET
                statuses: 500,502,503
            - name: CircuitBreaker
              args:
                name: usersCB
                fallbackUri: forward:/fallback/users
          metadata:
            response-timeout: 5000
```

### Acceptance Criteria

- [x] Upstream returns 5xx → gateway retries (3 attempts, exponential backoff)
- [x] Upstream consistently fails → CB opens → 503 returned via fallback
- [x] Upstream recovers → CB half-opens → CB closes → traffic resumes
- [x] Upstream hangs → 504 Gateway Timeout
- [x] 4xx errors are NOT retried
- [x] `POST`/`PUT`/`DELETE` are NOT retried by default
- [x] Fallback response includes `status`, `error`, `route`, `correlationId`
- [x] Resilience4j metrics exposed (circuit state, call count)
- [x] Tests pass

---

## Milestone 5: Error Handling + Metrics + Tracing + Health ✅

**Objective**: Structured error responses, Prometheus metrics, OpenTelemetry tracing, and health probes.

### Files Created/Modified

| File | Purpose |
|------|---------|
| `pom.xml` | Added `opentelemetry-api` (compile), `opentelemetry-sdk` + `opentelemetry-sdk-testing` (test) |
| `src/main/java/gateway/common/exception/GlobalErrorHandler.java` | `ErrorWebExceptionHandler` returning structured JSON |
| `src/main/java/gateway/filter/CorrelationIdGlobalFilter.java` | Added OTel span → MDC bridge for `traceId`/`spanId` |
| `src/main/resources/application.yml` | Added OTel env vars section |
| `src/test/java/gateway/common/exception/GlobalErrorHandlerTest.java` | 6 unit tests |
| `src/test/java/gateway/integration/GatewayIntegrationTest.java` | Added 6 observability integration tests |
| `src/test/resources/application.yml` | Added management endpoints config |

### Acceptance Criteria

- [x] All error responses are structured JSON with `status`, `error`, `path`, `correlationId`, `timestamp`
- [x] `/actuator/health` returns UP
- [x] `/actuator/health/liveness` returns UP
- [x] `/actuator/health/readiness` returns UP
- [x] `/actuator/prometheus` returns Prometheus metrics
- [x] OTel SDK configured; `traceId`/`spanId` bridged to MDC from current span
- [x] `GlobalErrorHandler` handles 401, 403, 404, 500, BAD_REQUEST
- [x] No HTML whitelabel pages — all errors are `application/json`
- [x] All 39 tests pass
- [x] Spotless formatting passes

---

## Milestone 6: Docker Deployment ✅

**Objective**: Containerize the gateway with a production-grade Dockerfile and Docker Compose for local development.

### Files Created

| File | Purpose |
|------|---------|
| `Dockerfile` | Multi-stage build (Java 26, non-root user, port 8000) |
| `docker-compose.yml` | Build and run the gateway with environment variable passthrough |

### Usage

```bash
docker compose up --build
```

### Acceptance Criteria

- [x] `docker build` produces a working image
- [x] Non-root user in runtime container
- [x] Graceful shutdown via `server.shutdown=graceful`
- [x] All existing tests pass

---

## Milestone 7: Comprehensive Test Suite + Production Hardening

**Objective**: Full test coverage, edge case handling, and hardening for production readiness.

### Files to Create

| File | Purpose |
|------|---------|
| `src/test/java/gateway/integration/GatewayIntegrationTest.java` | (Expand) comprehensive integration tests |
| `src/test/resources/wiremock/mappings/*.json` | WireMock stubs for all route patterns |

### Tests to Add/Expand

**GatewayIntegrationTest.java** (complete test matrix):

| Category | Tests |
|----------|-------|
| Routing | Route by path, method, missing route (404) |
| Correlation ID | Present, absent, forwarded to upstream |
| Security | No token, invalid token, expired token, wrong issuer, valid token, admin role, missing role |
| CORS | Valid origin (200), invalid origin (403), preflight with credentials |
| Header manipulation | Headers added, removed, rewritten |
| Retry | 500 retry, retries exhausted, POST not retried, GET with timeout retry |
| Circuit Breaker | Open on 5xx, half-open on success, fallback response format |
| Timeout | Hanging upstream → 504, fast upstream → 200 |
| Auth + Resilience | Authenticated request + CB open → 503 with correlation ID |
| Error responses | 400, 401, 403, 404, 500, 503, 504 — all structured JSON |
| Health | `/health/liveness`, `/health/readiness`, `/health` |
| Metrics | `/prometheus` returns expected metrics |
| Headers | Security headers present, X-Powered-By removed |

### Production Hardening Config

```yaml
server:
  netty:
    connection-timeout: 5s
    max-initial-line-length: 8KB
    max-chunk-size: 16KB

spring:
  codec:
    max-in-memory-size: 256KB

  cloud:
    gateway:
      httpclient:
        connect-timeout: 3s
        response-timeout: 5s
        pool:
          type: ELASTIC
          max-connections: 500
          max-idle-time: 60s
          max-life-time: 300s
```

### Final Checklist

- [ ] `./mvnw test` passes with > 85% line coverage
- [ ] All 7 M1–M6 acceptance criteria are met
- [ ] No sensitive data in logs (passwords, tokens, PII)
- [ ] `server.shutdown=graceful` + `preStop` = zero-downtime deploys
- [ ] Gateway starts, routes traffic, and shuts down without errors
- [ ] README includes: build, run, test, and deploy instructions
- [ ] AGENTS.md written for AI coding assistance

---

## V2+ Feature Backlog

These are explicitly deferred to keep V1 lean:

| Feature | When | Why deferred |
|---------|------|-------------|
| **Rate limiting** | V2 | Requires Redis; not needed until traffic requires it |
| **API key auth** | V2 | Requires PostgreSQL; JWT covers V1 auth needs |
| **Dynamic routes** | V2 | Requires Redis; YAML routes suffice until ops team needs hot-reload |
| **Admin API** | V2 | No operational need until multiple teams own routes |
| **Request body validation** | V2 | Not a core gateway concern; validate upstream |
| **Custom metrics** | V2 | Micrometer auto-metrics cover V1 needs |
| **Grafana dashboards** | V2 | Prometheus metrics exposed; dashboards are ops team concern |
| **Performance tests** | V2 | Confirm stability before optimizing for throughput |
| **Chaos testing** | V3 | After production proves stable under normal conditions |
| **Canary / Blue-green** | V3 | Deployment pattern, not gateway feature |
| **WebSocket** | V3 | Not a current platform requirement |

---

## Delivery Checklist

```
M1: Foundation           ✓ pom.xml           ✓ application.yml   ✓ context test
M2: Correlation+Logging  ✓ CorrelationFilter  ✓ log4j2.xml+log-layout.json ✓ unit test
M3: Security             ✓ SecurityConfig    ✓ JWKS fixture      ✓ auth integration
M4: Resilience           ✓ FallbackController ✓ CB config        ✓ resil integration
M5: Observability        ✓ GlobalErrorHandler ✓ JSON error body  ✓ error unit test ✓ OTel ✓ Prometheus ✓ Health
M6: Docker               ✓ Dockerfile         ✓ docker-compose.yml
M7: Full Test Suite      ☐ integration test  ☐ WireMock stubs    ☐ >85% coverage
```

Each milestone is designed as a standalone deliverable. The gateway is usable (though not feature-complete) after M2. Production-ready after M6.
