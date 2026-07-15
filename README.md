# API Gateway

Spring Cloud Gateway 5.x front-door proxy for a microservices platform.

## Features

- **Authentication**: OAuth2 Resource Server with JWT bearer tokens (via JWKS)
- **Correlation IDs**: `X-Correlation-ID` header propagated across all requests
- **Resilience**: Circuit Breaker, Retry, Response Timeout, Fallback
- **Structured JSON Logging**: Log4j2 with JsonTemplateLayout (Loki/ELK/Datadog friendly)
- **OpenTelemetry**: Trace context propagation, `traceId`/`spanId` in structured logs (via OTel Java agent in production, SDK available in test)
- **Metrics**: Micrometer + Prometheus via `/actuator/prometheus`
- **Health Probes**: Liveness (`/actuator/health/liveness`), Readiness (`/actuator/health/readiness`)
- **Structured Error Responses**: JSON error body for all HTTP error statuses (no HTML whitelabel pages)

## Authentication

All routes under `/api/v1/**` require a valid JWT. Actuator endpoints (except `/health` and `/info`) require the `ROLE_ADMIN` authority. Public endpoints: `/actuator/health/**`, `/actuator/info`, `/fallback/**`.

Configure issuer and JWKS URI via environment variables:

```bash
export JWT_ISSUER_URI=https://your-auth-server.com
export JWKS_URI=https://your-auth-server.com/.well-known/jwks.json
```

## Configuration Strategy

A single `application.yml` works for all environments (12-Factor). Override via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DEFAULT_LOG_LEVEL` | `INFO` | Root log level |
| `JWT_ISSUER_URI` | `https://auth.example.com` | JWT issuer |
| `JWKS_URI` | `https://auth.example.com/.well-known/jwks.json` | JWKS endpoint |
| `GATEWAY_CORS_ORIGINS` | `https://app.example.com` | Allowed CORS origins |
| `OTEL_TRACES_EXPORTER` | `otlp` | OpenTelemetry trace exporter |

Tests use `src/test/resources/application.yml` for automatic overrides.

## Running Locally

```bash
./mvnw spring-boot:run
```

The gateway starts on port 8080.

## Logging

Structured JSON on stdout. Format defined in `log-layout.json` (Log4j2 JsonTemplateLayout). Fields: `timestamp`, `level`, `logger`, `message`, `thread`, `method`, `line`, `correlationId`, `traceId`, `spanId`, `exception`.

`traceId` and `spanId` are populated from the current OpenTelemetry span context. In production, attach the OTel Java agent for full auto-instrumentation.

## Observability Endpoints

| Endpoint | Purpose | Auth |
|----------|---------|------|
| `/actuator/health` | Overall health status | Public |
| `/actuator/health/liveness` | Liveness probe | Public |
| `/actuator/health/readiness` | Readiness probe | Public |
| `/actuator/info` | Build info | Public |
| `/actuator/prometheus` | Prometheus metrics | `ROLE_ADMIN` |

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

Tests use `mockJwt()`, WireMock, and Awaitility.

## Build

```bash
./mvnw clean package
```

## Docker

```bash
./mvnw jib:dockerBuild
```
