# API Gateway

Spring Cloud Gateway 5.x front-door proxy for a microservices platform.

## Features

- **Authentication**: OAuth2 Resource Server with JWT bearer tokens (via JWKS)
- **Correlation IDs**: `X-Correlation-ID` header propagated across all requests
- **Resilience**: Circuit Breaker, Retry, Response Timeout, Fallback
- **Structured JSON Logging**: Log4j2 with JsonTemplateLayout (Loki/ELK/Datadog friendly)
- **OpenTelemetry ready**: Trace and span IDs in logs when OTEL agent is active
- **Metrics**: Prometheus via Actuator

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

Tests use `src/test/resources/application.yml` for automatic overrides.

## Running Locally

```bash
./mvnw spring-boot:run
```

The gateway starts on port 8080.

## Logging

Structured JSON on stdout. Format defined in `log-layout.json` (Log4j2 JsonTemplateLayout). Fields: `timestamp`, `level`, `logger`, `message`, `thread`, `method`, `line`, `correlationId`, `traceId`, `spanId`, `exception`.

When OpenTelemetry agent is attached, `traceId` and `spanId` populate automatically (requires configuring OTEL Log4j2 MDC keys to `traceId`/`spanId`).

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
