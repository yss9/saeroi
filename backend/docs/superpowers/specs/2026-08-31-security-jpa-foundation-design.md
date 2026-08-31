# Security and JPA Foundation Design

## Goal

Add a production-oriented security baseline and a domain-oriented JPA/QueryDSL foundation to the existing Spring Boot service. The security layer must protect REST APIs with standard JWT processing and reuse the existing common response contract. The persistence layer must support type-safe dynamic queries without introducing speculative generic repositories or CRUD services.

## Scope

This change includes:

- Stateless Spring Security configuration.
- OAuth2 Resource Server JWT validation using a symmetric HS256 key.
- Access-token issuance as a reusable service, without a login endpoint.
- Role-claim conversion and method security.
- BCrypt-based password encoding.
- Common JSON responses for authentication and authorization failures.
- QueryDSL 5.1.0 with Jakarta Persistence support.
- A `JPAQueryFactory` bean and reorganized common JPA packages.
- Security, token, QueryDSL, auditing, and regression tests.

This change does not include login, signup, refresh tokens, token persistence or revocation, social login, an authorization server, CORS allowlists, domain entities, database migrations, or generic CRUD abstractions.

## Chosen Approaches

### Security

Use Spring Security OAuth2 Resource Server instead of a custom `OncePerRequestFilter`. Spring Security performs Bearer token extraction, signature and timestamp validation, `SecurityContext` population, and authentication failure routing. The service supplies a `JwtDecoder`, a `JwtEncoder`, a role converter, and JSON failure handlers.

The current monolith uses an HS256 key supplied through the environment. A future external authorization server can replace the decoder/encoder beans with an issuer/JWK configuration without changing controller authorization rules.

### Persistence

Use QueryDSL 5.1.0 with the Maven `jakarta` classifiers. QueryDSL's official 5.x line provides Jakarta package support through these classifiers. Domain repositories continue to extend `JpaRepository` directly and add a custom QueryDSL repository only when a real dynamic-query use case exists.

## Dependencies

Add these Spring Boot starters:

- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-resource-server`
- `spring-security-test` with test scope

Add QueryDSL dependencies with an explicit `5.1.0` property:

- `com.querydsl:querydsl-jpa:5.1.0:jakarta`
- `com.querydsl:querydsl-apt:5.1.0:jakarta` for annotation processing

The Maven compiler configuration must include the QueryDSL processor and the Jakarta Persistence/Annotation APIs required to generate Q types. Main-source Q types remain under `target/generated-sources/annotations`; Q types for test-only entities may be generated under `target/generated-test-sources/test-annotations`. Both paths stay below the already ignored `target` directory.

## Package Structure

```text
com.ssafy.upcycle.common
├── security
│   ├── SecurityErrorCode.java
│   ├── config
│   │   ├── JwtProperties.java
│   │   ├── JwtSecurityConfig.java
│   │   └── SecurityConfig.java
│   ├── handler
│   │   ├── RestAccessDeniedHandler.java
│   │   └── RestAuthenticationEntryPoint.java
│   └── jwt
│       ├── AccessToken.java
│       ├── JwtAuthorityConverter.java
│       └── JwtTokenService.java
└── jpa
    ├── config
    │   ├── JpaAuditingConfig.java
    │   └── QuerydslConfig.java
    └── entity
        └── BaseEntity.java
```

The existing `common.config.JpaAuditingConfig` and `common.entity.BaseEntity` move to the `common.jpa` hierarchy. Their behavior does not change. Existing tests and imports move with them.

Future domain persistence code follows this shape only when the domain feature is implemented:

```text
<domain>
├── domain
│   └── <Entity>.java
└── repository
    ├── <Entity>Repository.java
    └── custom
        └── ...                 # only when a dynamic query is required
```

## Security Configuration

`SecurityConfig` provides one `SecurityFilterChain` with these rules:

- Disable CSRF because the API does not use cookie-backed sessions.
- Set session creation policy to `STATELESS`.
- Permit error dispatches so application error handling does not recurse into authentication.
- Permit `/api/v1/auth/**`.
- Require authentication for every other request.
- Enable OAuth2 Resource Server JWT processing.
- Use `RestAuthenticationEntryPoint` for 401 failures.
- Use `RestAccessDeniedHandler` for 403 failures.
- Enable method-level authorization with `@EnableMethodSecurity`.

CORS is intentionally not enabled. No production frontend origin has been selected, and an overly broad default would weaken the security baseline. A deployment-specific allowlist will be added when the frontend origin is known.

Expose a `PasswordEncoder` through `PasswordEncoderFactories.createDelegatingPasswordEncoder()`. Its default encoding is BCrypt and the `{bcrypt}` prefix preserves future algorithm migration.

## JWT Configuration and Contract

Bind the following properties under `app.security.jwt`:

```yaml
app:
  security:
    jwt:
      secret: ${JWT_SECRET}
      issuer: ${JWT_ISSUER:upcycle}
      access-token-ttl: ${JWT_ACCESS_TOKEN_TTL:PT30M}
```

Rules:

- `secret` is required and has no production default.
- `secret` must be valid Base64 that decodes to at least 32 bytes.
- `issuer` must not be blank.
- `access-token-ttl` must be positive.
- Property and key classes must not provide a `toString` that exposes the secret.
- Test resources provide a separate fixed 256-bit Base64 key.

`JwtSecurityConfig` derives one `SecretKey` and creates:

- An HS256 `NimbusJwtDecoder` with issuer and standard timestamp validation.
- A `NimbusJwtEncoder` using the same key.
- A UTC `Clock` bean for deterministic token creation and tests.

`JwtTokenService` exposes:

```text
AccessToken issueAccessToken(String subject, Collection<String> roles)
```

`AccessToken` contains only the encoded token value and expiration `Instant`. It is immutable and does not provide a `toString` implementation that could expose the Bearer token in logs. The issued token contains:

- `iss`: configured issuer
- `sub`: authenticated user identifier
- `iat`: issuance instant
- `exp`: issuance instant plus configured TTL
- `roles`: role names without the `ROLE_` prefix

The service rejects blank subjects, null role collections, and blank role entries. It does not create refresh tokens.

`JwtAuthorityConverter` reads `roles`, prefixes each value with `ROLE_`, removes duplicates, and creates deterministic authority ordering. The JWT `sub` claim remains the authentication name.

## Security Error Responses

`SecurityErrorCode` implements the existing `ErrorCode` interface:

| Code | HTTP status | Message |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | `인증이 필요합니다.` |
| `FORBIDDEN` | 403 | `접근 권한이 없습니다.` |

Security filter failures occur before MVC exception handling, so the entry point and access-denied handler serialize `ApiResponse.error(SecurityErrorCode)` directly with the configured Jackson `ObjectMapper`.

The response content type is JSON with UTF-8. Client responses never include token contents, decoder messages, exception types, stack traces, secrets, or authorization details.

Example:

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다.",
  "data": null,
  "timestamp": "2026-08-31T12:00:00"
}
```

## JPA and QueryDSL Configuration

`BaseEntity` remains an ID-free `@MappedSuperclass` with protected construction and JPA auditing for `createdAt` and `updatedAt`.

`JpaAuditingConfig` continues to enable auditing. `QuerydslConfig` receives the Spring-managed shared `EntityManager` proxy and exposes `JPAQueryFactory`. The factory can be injected into domain-specific custom repository implementations.

No `BaseRepository`, generic QueryDSL repository, generic CRUD service, soft-delete policy, paging abstraction, or production example entity is introduced. `JpaRepository` already supplies the common persistence contract, and additional abstraction requires a demonstrated domain need.

Existing database policy remains unchanged:

- MySQL connection values come from `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
- Production Hibernate schema behavior is `ddl-auto: validate`.
- Open Session in View is disabled.
- Tests use H2 in MySQL compatibility mode with `create-drop`.

## Request and Data Flow

For protected requests:

1. Spring Security extracts the Bearer token.
2. `JwtDecoder` validates HS256 signature, issuer, timestamps, and token structure.
3. `JwtAuthorityConverter` converts the `roles` claim.
4. Spring stores the authenticated JWT principal in the `SecurityContext`.
5. URL and method authorization run before the controller.
6. Authentication and authorization failures are written with the common JSON response.

For persistence queries:

1. A domain service calls its domain repository inside its own transaction boundary.
2. Simple repositories use `JpaRepository` methods.
3. A custom repository injects `JPAQueryFactory` only when it needs dynamic predicates.
4. Hibernate executes against MySQL in production and MySQL-compatible H2 in tests.

## Testing

Implementation follows test-driven development and covers:

1. JWT configuration rejecting missing, malformed, short, or invalid-duration values.
2. Access-token issuance and decoding with exact issuer, subject, roles, issuance, and expiration claims.
3. Invalid signature, expired token, and incorrect issuer rejection.
4. Role conversion, duplicate removal, prefixing, and deterministic ordering.
5. Public `/api/v1/auth/**` access without authentication.
6. Protected-route 401 response with the complete `ApiResponse` envelope.
7. Valid-token access to a protected test endpoint.
8. Insufficient-role 403 response with the complete `ApiResponse` envelope.
9. BCrypt-based password encoder behavior.
10. QueryDSL Q-type generation during Maven compilation.
11. An H2 integration test that persists a test entity and retrieves it through `JPAQueryFactory`.
12. Existing BaseEntity auditing behavior after package relocation.
13. The complete Maven verification suite and executable JAR build.

No production-only test endpoint or sample entity is added. Security test controllers and QueryDSL test entities remain in test sources.

## Completion Criteria

The foundation is complete when:

- Security and QueryDSL dependencies resolve through the Maven Wrapper.
- The application context starts with test-only security properties.
- Every listed security and persistence behavior has a passing test.
- Q types are generated under `target` and no generated source is tracked by Git.
- Existing response, exception, package, and auditing tests remain green.
- `mvnw clean verify` reports `BUILD SUCCESS` and produces the executable JAR.
- The final diff contains no embedded production secret or credential.

## References

- Spring Security, OAuth2 Resource Server JWT: <https://docs.spring.io/spring-security/reference/servlet/oauth2-resource-server/jwt.html>
- Spring Security, OAuth2 overview and custom JWT: <https://docs.spring.io/spring-security/reference/servlet/oauth2/>
- QueryDSL official releases: <https://github.com/querydsl/querydsl/releases>
