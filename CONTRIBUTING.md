# Contributing

Thanks for your interest in contributing to the Notification Platform.

## Project Setup

### Prerequisites

- **JDK 23** (Temurin recommended)
- **Apache Maven 3.9+**
- **Docker Desktop** (for running Kafka, PostgreSQL, Redis locally)
- **IntelliJ IDEA** (preferred, though any IDE works)

### Running Locally

1.  Start infrastructure:

    ```bash
    docker compose up -d
    ```

    This starts Kafka, Zookeeper, and Kafka UI.

2.  Start each service:

    ```bash
    # Terminal 1 - delivery-tracker (port 8003)
    mvn spring-boot:run -pl delivery-tracker

    # Terminal 2 - template-service (port 8002)
    mvn spring-boot:run -pl template-service

    # Terminal 3 - notification-service (port 8001)
    mvn spring-boot:run -pl notification-service
    ```

### Building

```bash
mvn clean compile
```

### Running Tests

```bash
mvn clean verify
```

To run a specific module:

```bash
mvn clean test -pl notification-service
```

## Branch Naming

Use a consistent prefix followed by a short description:

- `feat/` - new features
- `fix/` - bug fixes
- `chore/` - tooling, CI, or dependency updates
- `docs/` - documentation-only changes

Examples: `feat/add-sms-retry`, `fix/kafka-ack-timeout`.

## Commit Messages

Use [conventional commit](https://www.conventionalcommits.org/) style:

```
<type>(<scope>): <short description>

<optional body>
```

Examples:

- `feat(notification): add webhook HMAC signature verification`
- `fix(delivery-tracker): handle negative page offset in getFailedAttempts`
- `chore(ci): pin GitHub Actions runner versions`

Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.

Scopes: `notification-service`, `template-service`, `delivery-tracker`, `common`, `ci`.

## Pull Request Workflow

1.  Create a feature/fix branch from `main`.
2.  Make your changes and commit them using conventional commits.
3.  Run `mvn clean verify` locally before pushing.
4.  Open a PR against `main` using the PR template.
5.  Ensure the CI build passes.
6.  Request a review from a maintainer.

## Coding Guidelines

- Follow the existing code style (package layout, naming conventions, record usage).
- Keep hexagonal architecture boundaries: domain → application → adapter.
- Use `@Slf4j` for logging; avoid `System.out`.
- Validate inputs at the adapter layer (REST DTOs, Kafka payloads).
- Add unit tests for new use cases and service logic.
- Do **not** commit secrets or hardcoded credentials.
