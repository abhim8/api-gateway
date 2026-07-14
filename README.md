# API Gateway

Spring Cloud Gateway 5.x front-door proxy for a microservices platform.

## Authentication

This gateway uses **Spring Security OAuth2 Resource Server** with JWT bearer tokens.

- All routes under `/api/v1/**` require a valid JWT
- Actuator endpoints (except `/health` and `/info`) require the `ROLE_ADMIN` authority
- Public endpoints: `/actuator/health/**`, `/actuator/info`, `/fallback/**`

Configure issuer and JWKS URI via environment variables:

```bash
export JWT_ISSUER_URI=https://your-auth-server.com
export JWKS_URI=https://your-auth-server.com/.well-known/jwks.json
```

## Running Locally

```bash
./mvnw spring-boot:run
```

The gateway starts on port 8080.

## Security Configuration

See `src/main/java/gateway/config/SecurityConfig.java` for authorization rules.

See `src/main/resources/application.yml` under `spring.security.oauth2` for JWT configuration.

## Test Instructions

```bash
# Run all tests (unit + integration)
./mvnw clean verify

# Run only unit tests
./mvnw test

# Run only integration tests
./mvnw verify
```

Tests use:
- **mockJwt()** from spring-security-test for authenticated requests
- **WireMock** for upstream service stubs (where applicable)

## Build

```bash
./mvnw clean package
```

## Docker

```bash
./mvnw jib:dockerBuild
```
