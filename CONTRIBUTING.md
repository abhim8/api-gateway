# Contributing

Thanks for your interest in contributing to the API Gateway.

## Project Setup

### Prerequisites

- **Java 26** (Temurin recommended)
- **Docker Desktop** (optional, for containerized runs)

### Build and Test

```bash
./mvnw clean verify
```

### Run Locally

```bash
./mvnw spring-boot:run
```

Starts on port 8000 with mock authentication (no external dependencies).

### Docker

```bash
docker build -t api-gateway .
docker run -p 8000:8000 -e GATEWAY_AUTHENTICATION_PROVIDER=mock api-gateway
```

## Branch Naming

- `feat/` - new features
- `fix/` - bug fixes
- `chore/` - tooling, CI, or dependency updates
- `docs/` - documentation-only changes

Examples: `feat/add-rate-limiting`, `fix/circuit-breaker-timeout`.

## Commit Messages

Use [conventional commit](https://www.conventionalcommits.org/) style:

```
<type>(<scope>): <short description>

<optional body>
```

Examples:

- `feat(auth): add remote authentication provider`
- `fix(filter): handle null correlation ID header`
- `chore(deps): bump spring-cloud-gateway to 5.1.2`

Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.

Scopes: `auth`, `filter`, `config`, `common`, `deps`, `docs`, `ci`.

## Pull Request Process

1. Create a feature/fix branch from `main`.
2. Make your changes and commit using conventional commits.
3. Run `./mvnw clean verify` locally before pushing.
4. Open a PR against `main` using the pull request template.
5. Request a review from a maintainer.

## Coding Standards

- **Reactive first**: `Mono<T>` / `Flux<T>` for all async code. No blocking calls.
- **Immutability**: Use Java `record` for DTOs, `final` fields for everything else.
- **No nulls**: Use `Mono.empty()` or `Optional` instead of null.
- **Constructor injection**: No field injection (`@Autowired` on fields).
- **Small classes**: One responsibility. Under 100 lines unless unavoidable.
- **Configuration over code**: If Spring Cloud Gateway has a built-in filter factory for it, use it - don't write custom code.
- Do **not** commit secrets or hardcoded credentials.
