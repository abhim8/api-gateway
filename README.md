# API Gateway

Spring Cloud Gateway 5.x front-door proxy for a microservices platform.

## Features

- **Authentication**: Delegated to a dedicated Auth Platform via `AuthenticationProvider`
- **Correlation IDs**: `X-Correlation-ID` header propagated across all requests
- **Resilience**: Circuit Breaker, Retry, Response Timeout, Fallback
- **Structured JSON Logging**: Log4j2 with JsonTemplateLayout (Loki/ELK/Datadog friendly)
- **OpenTelemetry**: Trace context propagation, `traceId`/`spanId` in structured logs (via OTel Java agent in production, SDK available in test)
- **Metrics**: Micrometer + Prometheus via `/actuator/prometheus`
- **Health Probes**: Liveness (`/actuator/health/liveness`), Readiness (`/actuator/health/readiness`)
- **Structured Error Responses**: JSON error body for all HTTP error statuses (no HTML whitelabel pages)

## Authentication

Every request **must** go through an `AuthenticationProvider`. The gateway never bypasses authentication.

Two authentication modes are available:

| Mode | Description |
|------|-------------|
| `mock` (default) | `MockAuthenticationProvider` — always returns `authenticated = true`. Suitable for local development. |
| `remote` | `RemoteAuthenticationProvider` — reserved for future Auth Platform integration. Currently throws `UnsupportedOperationException`. |

Select the mode via `application.yml`:

```yaml
gateway:
  auth:
    mode: mock
```

Or override with an environment variable:

```bash
export GATEWAY_AUTH_MODE=remote
```

## Configuration Strategy

A single `application.yml` works for all environments (12-Factor). Override via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DEFAULT_LOG_LEVEL` | `INFO` | Root log level |
| `GATEWAY_AUTH_MODE` | `mock` | Auth mode (`mock` or `remote`) |
| `GATEWAY_CORS_ORIGINS` | `https://app.example.com` | Allowed CORS origins |
| `OTEL_TRACES_EXPORTER` | `otlp` | OpenTelemetry trace exporter |

Tests use `src/test/resources/application.yml` for automatic overrides.

## Running Locally

```bash
./mvnw spring-boot:run
```

The gateway starts on port 8000.

## Logging

Structured JSON on stdout. Format defined in `log-layout.json` (Log4j2 JsonTemplateLayout). Fields: `timestamp`, `level`, `logger`, `message`, `thread`, `method`, `line`, `correlationId`, `traceId`, `spanId`, `exception`.

`traceId` and `spanId` are populated from the current OpenTelemetry span context. In production, attach the OTel Java agent for full auto-instrumentation.

## Observability Endpoints

| Endpoint | Purpose | Access |
|----------|---------|--------|
| `/actuator/health` | Overall health status | Public |
| `/actuator/health/liveness` | Liveness probe | Public |
| `/actuator/health/readiness` | Readiness probe | Public |
| `/actuator/info` | Build info | Public |
| `/actuator/prometheus` | Prometheus metrics | Public (no auth enforcement) |

## Error Responses

All HTTP errors return consistent JSON:

```json
{
  "timestamp": "2026-07-15T12:00:00.000Z",
  "status": 404,
  "error": "Not Found",
  "path": "/api/v1/nonexistent",
  "correlationId": "a1b2c3d4-..."
}
```

## Test Instructions

```bash
./mvnw clean verify
```

## Build

```bash
./mvnw clean package
```

## Docker

Build and run with Docker:

```bash
docker build -t api-gateway .
docker run -p 8000:8000 api-gateway
```

Or with Docker Compose:

```bash
docker compose up --build
```

Set environment variables via `--env-file` or `-e`:

```bash
docker run -p 8000:8000 \
  -e GATEWAY_AUTH_MODE=mock \
  api-gateway
```
