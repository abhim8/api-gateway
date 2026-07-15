# API Gateway

Spring Cloud Gateway 5.x front-door proxy for a microservices platform. Java 26, reactive (WebFlux/Netty), stateless.

## Features

- **Authentication**: Every request goes through an `AuthenticationProvider` — never bypassed
- **Correlation IDs**: `X-Correlation-ID` header on all requests and responses
- **Resilience**: Circuit breaker, retry, response timeout, structured fallback
- **Structured JSON Logging**: Log4j2 with JsonTemplateLayout (Loki/ELK/Datadog friendly)
- **OpenTelemetry**: Trace context propagation, `traceId`/`spanId` in structured logs
- **Metrics**: Micrometer + Prometheus via `/actuator/prometheus`
- **Health Probes**: Liveness (`/actuator/health/liveness`), Readiness (`/actuator/health/readiness`)
- **Error Responses**: JSON error body for all HTTP error statuses (no HTML whitelabel pages)

## Architecture

```
Request → CORS → Correlation ID → Authentication → Route Match → Resilience → Proxy → Response
                                                                                          
         MockAuthenticationProvider (dev/CI)   RemoteAuthenticationProvider (future)
         ┌─────────────────────────┐           ┌──────────────────────────────┐
         │ authenticated = true    │           │ throws UnsupportedOperation  │
         │ zero I/O, no JWT, no   │           │ (will call Auth Platform)    │
         │ crypto                  │           │                              │
         └─────────────────────────┘           └──────────────────────────────┘
```

Every request **must** pass authentication before routing. See [DESIGN.md](DESIGN.md) for detailed architecture.

## Request Lifecycle

```
Phase   Component                    Error
──────  ───────────────────────────  ─────────────
1       Netty HTTP parser            400
2       CorsGlobalFilter             403
3       CorrelationIdGlobalFilter    —
4       AuthenticationGlobalFilter   401 / 500
5       RouteLocator                 404
6       Retry filter                 502
7       Circuit breaker              503 + fallback
8       Proxy (timeout)              504
9       Post-filters + ErrorHandler  —
```

## Authentication

| Mode | Provider | Behavior |
|------|----------|----------|
| `mock` (default) | `MockAuthenticationProvider` | Always `authenticated = true`. For local dev. |
| `remote` | `RemoteAuthenticationProvider` | Throws `UnsupportedOperationException`. Future Auth Platform integration. |

```yaml
gateway:
  auth:
    mode: mock
```

Override via `GATEWAY_AUTH_MODE` environment variable.

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `GATEWAY_AUTH_MODE` | `mock` | Auth mode (`mock` or `remote`) |
| `GATEWAY_CORS_ORIGINS` | `https://app.example.com` | Allowed CORS origins |
| `DEFAULT_LOG_LEVEL` | `INFO` | Root log level |
| `OTEL_TRACES_EXPORTER` | `otlp` | OpenTelemetry trace exporter |

## Package Structure

```
gateway/
├── GatewayApplication.java
├── config/AuthConfig.java
├── auth/          # AuthenticationProvider, AuthenticationResult, MockAuthenticationProvider, RemoteAuthenticationProvider
├── filter/        # AuthenticationGlobalFilter, CorrelationIdGlobalFilter
├── web/           # FallbackController
└── common/        # GlobalErrorHandler, HeaderConstants
```

## Running Locally

```bash
./mvnw spring-boot:run
```

Starts on port 8000.

## Testing

```bash
./mvnw clean verify
```

## Docker

```bash
docker build -t api-gateway .
docker run -p 8000:8000 -e GATEWAY_AUTH_MODE=mock api-gateway
```

Or with Docker Compose:

```bash
docker compose up --build
```

## Current Limitations

- `RemoteAuthenticationProvider` is a stub — it throws `UnsupportedOperationException` until the Auth Platform endpoint is available
- No rate limiting (requires Redis — V2+)
- No dynamic route management (requires Redis — V2+)
- No API key authentication (V2+)

See [ROADMAP.md](ROADMAP.md) for completed milestones and future plans.
