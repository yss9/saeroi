# Security Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protect the REST API with stateless Spring Security JWT processing, reusable access-token issuance, role conversion, and common JSON responses for 401 and 403 failures.

**Architecture:** Spring Security OAuth2 Resource Server owns Bearer token extraction and validation. A validated configuration object derives an HS256 key for Nimbus decoder/encoder beans, while small token, authority, and response-handler components expose focused interfaces. Login and refresh-token workflows remain outside this foundation.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Security, OAuth2 Resource Server, Nimbus JOSE JWT, JUnit 5, AssertJ, MockMvc, Spring Security Test, Maven Wrapper

**Spec:** `docs/superpowers/specs/2026-08-31-security-jpa-foundation-design.md`

## Global Constraints

- Security is stateless and does not create or use HTTP sessions.
- `/api/v1/auth/**` and error dispatches are public; every other request requires authentication.
- JWT uses HS256, validates issuer and timestamps, and reads roles from the `roles` claim.
- `JWT_SECRET` is required Base64 that decodes to at least 32 bytes; no production default is allowed.
- Default issuer is `upcycle`; default access-token TTL is `PT30M`; TTL must be positive.
- Tokens contain only `iss`, `sub`, `iat`, `exp`, and `roles` application claims.
- Client responses never expose token values, secrets, decoder messages, exception classes, or stack traces.
- Authentication and authorization failures use the existing five-field `ApiResponse` envelope.
- Password encoding uses delegating encoding with BCrypt as the default.
- No login endpoint, refresh token, CORS allowlist, social login, or authorization server is added.

---

## File Map

- `pom.xml`: Spring Security, OAuth2 Resource Server, and security-test dependencies.
- `src/main/resources/application.yml`: environment-backed JWT settings.
- `src/test/resources/application.yml`: isolated test key and JWT settings.
- `src/main/java/com/ssafy/upcycle/common/security/config/JwtProperties.java`: validated configuration that retains a `SecretKey`, never the raw secret.
- `src/main/java/com/ssafy/upcycle/common/security/config/JwtSecurityConfig.java`: Clock, Nimbus decoder, and Nimbus encoder beans.
- `src/main/java/com/ssafy/upcycle/common/security/jwt/AccessToken.java`: immutable token value and expiry without secret-revealing `toString`.
- `src/main/java/com/ssafy/upcycle/common/security/jwt/JwtTokenService.java`: access-token issuer.
- `src/main/java/com/ssafy/upcycle/common/security/jwt/JwtAuthorityConverter.java`: deterministic `roles` to `ROLE_*` mapping.
- `src/main/java/com/ssafy/upcycle/common/security/SecurityErrorCode.java`: 401 and 403 public contracts.
- `src/main/java/com/ssafy/upcycle/common/security/handler/RestAuthenticationEntryPoint.java`: JSON 401 writer.
- `src/main/java/com/ssafy/upcycle/common/security/handler/RestAccessDeniedHandler.java`: JSON 403 writer.
- `src/main/java/com/ssafy/upcycle/common/security/config/SecurityConfig.java`: filter chain, method security, and password encoder.
- `src/test/java/com/ssafy/upcycle/common/security/config/JwtPropertiesTests.java`: key/property rejection tests.
- `src/test/java/com/ssafy/upcycle/common/security/jwt/JwtTokenServiceTests.java`: issuance, decode, claim, and validation tests.
- `src/test/java/com/ssafy/upcycle/common/security/jwt/JwtAuthorityConverterTests.java`: role mapping tests.
- `src/test/java/com/ssafy/upcycle/common/security/config/SecurityConfigTests.java`: HTTP-level 200/401/403 contract tests.

---

### Task 1: Security Dependencies and Validated JWT Infrastructure

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`
- Create: `src/main/java/com/ssafy/upcycle/common/security/config/JwtProperties.java`
- Create: `src/main/java/com/ssafy/upcycle/common/security/config/JwtSecurityConfig.java`
- Test: `src/test/java/com/ssafy/upcycle/common/security/config/JwtPropertiesTests.java`

**Interfaces:**
- Produces: `JwtProperties.secretKey(): SecretKey`, `issuer(): String`, `accessTokenTtl(): Duration`.
- Produces: beans `Clock`, `JwtDecoder`, and `JwtEncoder` for later tasks.

- [ ] **Step 1: Add the dependencies and failing property tests**

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

Create `JwtPropertiesTests`:

```java
package com.ssafy.upcycle.common.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtPropertiesTests {

    private static final String VALID_SECRET =
            Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void acceptsAValidConfigurationWithoutExposingRawSecret() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, "upcycle", Duration.ofMinutes(30));

        assertThat(properties.secretKey().getEncoded()).hasSize(32);
        assertThat(properties.issuer()).isEqualTo("upcycle");
        assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.toString()).doesNotContain(VALID_SECRET);
    }

    @Test
    void rejectsMissingMalformedAndShortSecrets() {
        assertThatThrownBy(() -> new JwtProperties(null, "upcycle", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("not-base64", "upcycle", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);
        assertThatThrownBy(() -> new JwtProperties(shortSecret, "upcycle", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankIssuerAndNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, " ", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, "upcycle", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, "upcycle", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

```powershell
.\mvnw.cmd -Dtest=JwtPropertiesTests test
```

Expected: test compilation fails because `JwtProperties` does not exist.

- [ ] **Step 3: Implement validated properties**

```java
package com.ssafy.upcycle.common.security.config;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public final class JwtProperties {

    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKey secretKey;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtProperties(String secret, String issuer, Duration accessTokenTtl) {
        this.secretKey = decodeSecret(secret);
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secretKey.getEncoded(), "HmacSHA256");
    }

    public String issuer() {
        return issuer;
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    private static SecretKey decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT secret must be valid Base64", exception);
        }
        if (decoded.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("JWT secret must decode to at least 32 bytes");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }
}
```

- [ ] **Step 4: Configure Nimbus beans and environment properties**

Create `JwtSecurityConfig`:

```java
package com.ssafy.upcycle.common.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtSecurityConfig {

    @Bean
    public Clock jwtClock() {
        return Clock.systemUTC();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey()).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        ImmutableSecret<SecurityContext> source = new ImmutableSecret<>(properties.secretKey());
        return new NimbusJwtEncoder(source);
    }
}
```

Append to main `application.yml`:

```yaml
app:
  security:
    jwt:
      secret: ${JWT_SECRET}
      issuer: ${JWT_ISSUER:upcycle}
      access-token-ttl: ${JWT_ACCESS_TOKEN_TTL:PT30M}
```

Append to test `application.yml` using the Base64 encoding of exactly 32 zero bytes:

```yaml
app:
  security:
    jwt:
      secret: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
      issuer: upcycle-test
      access-token-ttl: PT30M
```

- [ ] **Step 5: Run focused and context tests**

```powershell
.\mvnw.cmd '-Dtest=JwtPropertiesTests,UpcycleApplicationTests' test
```

Expected: 4 tests pass: three property tests and the existing context test.

- [ ] **Step 6: Commit JWT infrastructure**

```powershell
git add pom.xml src/main/resources/application.yml src/test/resources/application.yml src/main/java/com/ssafy/upcycle/common/security/config/JwtProperties.java src/main/java/com/ssafy/upcycle/common/security/config/JwtSecurityConfig.java src/test/java/com/ssafy/upcycle/common/security/config/JwtPropertiesTests.java
git commit -m "feat: JWT 보안 기반 설정 추가"
```

---

### Task 2: Access Token Issuance and Authority Conversion

**Files:**
- Create: `src/main/java/com/ssafy/upcycle/common/security/jwt/AccessToken.java`
- Create: `src/main/java/com/ssafy/upcycle/common/security/jwt/JwtTokenService.java`
- Create: `src/main/java/com/ssafy/upcycle/common/security/jwt/JwtAuthorityConverter.java`
- Test: `src/test/java/com/ssafy/upcycle/common/security/jwt/JwtTokenServiceTests.java`
- Test: `src/test/java/com/ssafy/upcycle/common/security/jwt/JwtAuthorityConverterTests.java`

**Interfaces:**
- Consumes: `JwtProperties`, `JwtEncoder`, `JwtDecoder`, and `Clock` from Task 1.
- Produces: `JwtTokenService.issueAccessToken(String, Collection<String>): AccessToken`.
- Produces: `JwtAuthorityConverter` as `Converter<Jwt, AbstractAuthenticationToken>`.

- [ ] **Step 1: Write failing token and authority tests**

Create tests that use a fixed UTC clock, the Task 1 properties/configuration, and real Nimbus encoding/decoding:

```java
@Test
void issuesAndDecodesAccessTokenWithExactClaims() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    AccessToken token = tokenService(Clock.fixed(now, ZoneOffset.UTC))
            .issueAccessToken("user-1", List.of("USER", "ADMIN", "USER"));

    Jwt jwt = decoder.decode(token.value());

    assertThat(jwt.getIssuer().toString()).isEqualTo("upcycle-test");
    assertThat(jwt.getSubject()).isEqualTo("user-1");
    assertThat(jwt.getIssuedAt()).isEqualTo(now);
    assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
    assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
    assertThat(token.toString()).doesNotContain(token.value());
}

@Test
void rejectsInvalidTokenInputs() {
    JwtTokenService service = tokenService(Clock.systemUTC());
    assertThatThrownBy(() -> service.issueAccessToken(" ", List.of("USER")))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.issueAccessToken("user-1", null))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.issueAccessToken("user-1", List.of("")))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void decoderRejectsWrongIssuerExpiredAndForeignSignature() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    String wrongIssuer = encode(encoder, "other", now.minusSeconds(1), now.plusSeconds(300));
    String expired = encode(encoder, "upcycle-test", now.minusSeconds(600), now.minusSeconds(300));
    String foreign = encode(foreignEncoder(), "upcycle-test", now.minusSeconds(1), now.plusSeconds(300));

    assertThatThrownBy(() -> decoder.decode(wrongIssuer))
            .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> decoder.decode(expired))
            .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> decoder.decode(foreign))
            .isInstanceOf(JwtException.class);
}

private String encode(JwtEncoder source, String issuer, Instant issuedAt, Instant expiresAt) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("user-1")
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("roles", List.of("USER"))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return source.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
}

private JwtEncoder foreignEncoder() {
    byte[] key = new byte[32];
    key[0] = 1;
    JwtProperties foreign = new JwtProperties(
            Base64.getEncoder().encodeToString(key), "upcycle-test", Duration.ofMinutes(30));
    return new JwtSecurityConfig().jwtEncoder(foreign);
}
```

Import `ChronoUnit`, the JWT encoder/claim/header/exception types shown above, and `Base64`. The test fixture must construct `encoder` and `decoder` from the same Task 1 properties, while `foreignEncoder()` deliberately uses a different 256-bit key.

Create authority tests using `Jwt.withTokenValue("token")` with required headers/timestamps:

```java
@Test
void convertsRolesWithPrefixDeduplicationAndStableOrdering() {
    Jwt jwt = jwtWithRoles(List.of("USER", "ADMIN", "USER"));

    AbstractAuthenticationToken authentication = converter.convert(jwt);

    assertThat(authentication).isNotNull();
    assertThat(authentication.getName()).isEqualTo("user-1");
    assertThat(authentication.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_ADMIN", "ROLE_USER");
}

@Test
void missingRolesProducesNoAuthorities() {
    assertThat(converter.convert(jwtWithRoles(null)).getAuthorities()).isEmpty();
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

```powershell
.\mvnw.cmd '-Dtest=JwtTokenServiceTests,JwtAuthorityConverterTests' test
```

Expected: compilation fails because the three JWT classes do not exist.

- [ ] **Step 3: Implement immutable token and token service**

```java
package com.ssafy.upcycle.common.security.jwt;

import java.time.Instant;
import java.util.Objects;

public final class AccessToken {
    private final String value;
    private final Instant expiresAt;

    public AccessToken(String value, Instant expiresAt) {
        this.value = Objects.requireNonNull(value);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public String value() {
        return value;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
```

```java
package com.ssafy.upcycle.common.security.jwt;

import com.ssafy.upcycle.common.security.config.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issueAccessToken(String subject, Collection<String> roles) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject must not be blank");
        }
        if (roles == null) {
            throw new IllegalArgumentException("JWT roles must not be null");
        }
        TreeSet<String> normalizedRoles = new TreeSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("JWT role must not be blank");
            }
            normalizedRoles.add(role);
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.copyOf(normalizedRoles))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, expiresAt);
    }
}
```

- [ ] **Step 4: Implement authority conversion**

```java
package com.ssafy.upcycle.common.security.jwt;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class JwtAuthorityConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Collection<SimpleGrantedAuthority> authorities = roles == null
                ? List.of()
                : roles.stream()
                        .filter(Objects::nonNull)
                        .filter(role -> !role.isBlank())
                        .collect(Collectors.toCollection(TreeSet::new))
                        .stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
```

- [ ] **Step 5: Run focused tests and commit**

```powershell
.\mvnw.cmd '-Dtest=JwtTokenServiceTests,JwtAuthorityConverterTests' test
git add src/main/java/com/ssafy/upcycle/common/security/jwt src/test/java/com/ssafy/upcycle/common/security/jwt
git commit -m "feat: JWT Access Token 서비스 추가"
```

Expected: all JWT service and authority tests pass.

---

### Task 3: Security Filter Chain and Common Failure Responses

**Files:**
- Create: `src/main/java/com/ssafy/upcycle/common/security/SecurityErrorCode.java`
- Create: `src/main/java/com/ssafy/upcycle/common/security/handler/RestAuthenticationEntryPoint.java`
- Create: `src/main/java/com/ssafy/upcycle/common/security/handler/RestAccessDeniedHandler.java`
- Create: `src/main/java/com/ssafy/upcycle/common/security/config/SecurityConfig.java`
- Test: `src/test/java/com/ssafy/upcycle/common/security/config/SecurityConfigTests.java`

**Interfaces:**
- Consumes: Task 1 decoder beans, Task 2 `JwtAuthorityConverter`, and existing `ApiResponse`/`ErrorCode`.
- Produces: stateless `SecurityFilterChain`, `PasswordEncoder`, JSON 401/403 behavior, and method security.

- [ ] **Step 1: Write HTTP-level failing tests**

Use `@SpringBootTest`, `@AutoConfigureMockMvc`, and a test-only imported controller with:

```java
@RestController
static class SecurityTestController {
    @GetMapping("/api/v1/auth/test")
    ApiResponse<String> publicEndpoint() {
        return ApiResponse.success("public");
    }

    @GetMapping("/test/security/protected")
    ApiResponse<String> protectedEndpoint() {
        return ApiResponse.success("protected");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/test/security/admin")
    ApiResponse<String> adminEndpoint() {
        return ApiResponse.success("admin");
    }
}
```

Cover these exact behaviors:

```java
@Test
void publicAuthPathDoesNotRequireToken() throws Exception {
    mockMvc.perform(get("/api/v1/auth/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("public"));
}

@Test
void protectedPathReturnsCommonUnauthorizedResponse() throws Exception {
    mockMvc.perform(get("/test/security/protected"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
            .andExpect(jsonPath("$.data").value(nullValue()))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
}

@Test
void validTokenCanAccessProtectedPath() throws Exception {
    String token = tokenService.issueAccessToken("user-1", List.of("USER")).value();
    mockMvc.perform(get("/test/security/protected")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
}

@Test
void insufficientRoleReturnsCommonForbiddenResponse() throws Exception {
    String token = tokenService.issueAccessToken("user-1", List.of("USER")).value();
    mockMvc.perform(get("/test/security/admin")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.data").value(nullValue()));
}

@Test
void passwordEncoderUsesDelegatingBcrypt() {
    String encoded = passwordEncoder.encode("password");
    assertThat(encoded).startsWith("{bcrypt}");
    assertThat(passwordEncoder.matches("password", encoded)).isTrue();
}
```

- [ ] **Step 2: Run the HTTP tests and verify RED**

```powershell
.\mvnw.cmd -Dtest=SecurityConfigTests test
```

Expected: compilation fails because the security error, handlers, and filter configuration do not exist.

- [ ] **Step 3: Add error codes and JSON handlers**

`SecurityErrorCode` must implement `ErrorCode` with:

```java
UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
```

Both handlers inject the application `ObjectMapper`, set the status and `MediaType.APPLICATION_JSON_VALUE`, set UTF-8, and write exactly:

```java
objectMapper.writeValue(response.getWriter(), ApiResponse.error(SecurityErrorCode.UNAUTHORIZED));
```

or:

```java
objectMapper.writeValue(response.getWriter(), ApiResponse.error(SecurityErrorCode.FORBIDDEN));
```

The handlers do not log or serialize the received security exception.

- [ ] **Step 4: Implement the filter chain**

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public JwtAuthorityConverter jwtAuthorityConverter() {
        return new JwtAuthorityConverter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthorityConverter authorityConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authorityConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
```

- [ ] **Step 5: Add invalid-token regression cases**

Extend `SecurityConfigTests` to assert malformed Bearer tokens return the same 401 envelope and that the serialized body does not contain the token value, `JwtException`, or decoder messages.

```java
@Test
void invalidTokenDoesNotLeakDetails() throws Exception {
    String invalidToken = "secret-invalid-token";
    String body = mockMvc.perform(get("/test/security/protected")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain(invalidToken, "JwtException", "Invalid");
}
```

- [ ] **Step 6: Run focused and full tests**

```powershell
.\mvnw.cmd -Dtest=SecurityConfigTests test
.\mvnw.cmd test
```

Expected: all security HTTP tests pass and all pre-existing tests remain green.

- [ ] **Step 7: Inspect and commit the completed security foundation**

```powershell
git diff --check
rg -n "JWT_SECRET|secret-invalid-token|getMessage\(\)|printStackTrace" src/main/java
git status --short
git add src/main/java/com/ssafy/upcycle/common/security src/test/java/com/ssafy/upcycle/common/security
git commit -m "feat: 공통 Security 필터 구조 추가"
```

Expected: no raw secret, test marker, stack trace printing, or exception message serialization exists in production code.
