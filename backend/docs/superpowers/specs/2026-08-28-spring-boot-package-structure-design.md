# Spring Boot Package Structure Design

## Goal

Create a minimal, runnable Spring Boot project in the repository root. The project uses Maven and organizes source code by the business domains requested by the team.

## Technology Baseline

- Build tool: Maven Wrapper
- Language: Java 21
- Framework: the current stable Spring Boot 3.x release available at implementation time
- Group ID: `com.ssafy`
- Artifact ID: `upcycle`
- Base package: `com.ssafy.upcycle`

## Project Structure

The main application class lives directly in `com.ssafy.upcycle` so Spring component scanning includes every domain package below it.

The following first-level packages are created under the base package:

- `user`
- `material`
- `trade`
- `inventory`
- `processing`
- `production`
- `product`
- `traceability`
- `blockchain`
- `common`

Each package initially contains only a `package-info.java` file. Empty controllers, services, repositories, entities, and DTOs are intentionally excluded because their responsibilities and dependencies have not yet been defined. As each domain is implemented, it can introduce its own `controller`, `service`, `domain`, `repository`, and `dto` subpackages.

## Dependencies

The initial project includes only broadly applicable dependencies:

- Spring Web
- Bean Validation
- Lombok
- Spring Boot Test

Database access, JPA, security, blockchain SDKs, and environment-specific integrations are outside this scaffold because their concrete requirements have not been chosen.

> **Superseded infrastructure baseline:** The paragraph above records the original minimal-scaffold decision. A later approved change added Spring Data JPA, the MySQL driver, MySQL datasource/JPA configuration, H2 test support, and JPA auditing; those current settings supersede only the original no-database/no-JPA boundary.

## Configuration and Runtime Behavior

The project includes a standard `application.yml` with only the application name. No database or external-service configuration is added. Running the main application starts the embedded web server without requiring infrastructure.

> **Superseded runtime baseline:** The paragraph above records the original runtime expectation. The later approved MySQL/JPA configuration now requires datasource environment values outside the test profile, while tests use the configured in-memory H2 database in MySQL compatibility mode.

## Error Handling and Data Flow

This scaffold does not expose application endpoints, so it introduces no request data flow or domain-specific error handling. Those concerns remain inside the domain packages when features are added. Shared error representations and exception handlers may later live in `common`, but no speculative abstractions are created now.

## Verification

Verification consists of:

1. A Spring context-load test using `@SpringBootTest`.
2. A Maven test run through the Maven Wrapper.
3. A package-tree check confirming all ten requested packages exist under `com.ssafy.upcycle`.

The scaffold is complete when the Maven test succeeds and the application context starts without external dependencies.
