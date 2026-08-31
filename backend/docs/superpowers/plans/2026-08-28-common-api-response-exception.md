# Common API Response and Exception Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a uniform generic REST response envelope and centralized, extensible exception handling for all backend domains.

**Architecture:** Immutable response objects define the public JSON contract. An `ErrorCode` interface allows common and domain enums to supply stable HTTP/error metadata, while `BusinessException` transports expected failures to one `@RestControllerAdvice`. The advice converts validation, framework, business, and unexpected exceptions without exposing rejected values or internal details.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Web MVC, Jakarta Bean Validation, JUnit 5, AssertJ, MockMvc, Maven Wrapper

**Spec:** `docs/superpowers/specs/2026-08-28-common-api-response-exception-design.md`

## Global Constraints

- Every application response uses the fields `success`, `code`, `message`, `data`, and `timestamp`.
- Default success responses use code `SUCCESS` and message `요청에 성공했습니다.`.
- `timestamp` uses `LocalDateTime` and Spring's ISO-8601 JSON serialization.
- Client-visible errors never include rejected values, stack traces, exception class names, or internal exception messages.
- Domain error enums implement `ErrorCode`; the `common` package must not depend on a domain package.
- Expected failures log at warning level without stack traces; unexpected failures log at error level with a stack trace.
- Validation details contain only `field` and `message`, retain the lexicographically smallest message per field, and sort by field name.
- No new production dependencies are required.

---

## File Map

- `src/main/java/com/ssafy/upcycle/common/response/ApiResponse.java`: immutable generic response envelope and named factories.
- `src/main/java/com/ssafy/upcycle/common/response/ValidationError.java`: safe validation detail DTO.
- `src/main/java/com/ssafy/upcycle/common/exception/ErrorCode.java`: extension contract for HTTP status, stable code, and client message.
- `src/main/java/com/ssafy/upcycle/common/exception/CommonErrorCode.java`: domain-independent common error catalog.
- `src/main/java/com/ssafy/upcycle/common/exception/BusinessException.java`: expected application failure carrying an `ErrorCode`.
- `src/main/java/com/ssafy/upcycle/common/exception/GlobalExceptionHandler.java`: centralized exception-to-HTTP response translation.
- `src/test/java/com/ssafy/upcycle/common/response/ApiResponseTests.java`: response factory contract tests.
- `src/test/java/com/ssafy/upcycle/common/exception/GlobalExceptionHandlerTests.java`: MockMvc and direct handler tests for every error category.

---

### Task 1: Response and Error Code Contracts

**Files:**
- Create: `src/main/java/com/ssafy/upcycle/common/exception/ErrorCode.java`
- Create: `src/main/java/com/ssafy/upcycle/common/exception/CommonErrorCode.java`
- Create: `src/main/java/com/ssafy/upcycle/common/response/ApiResponse.java`
- Create: `src/main/java/com/ssafy/upcycle/common/response/ValidationError.java`
- Test: `src/test/java/com/ssafy/upcycle/common/response/ApiResponseTests.java`

**Interfaces:**
- Produces: `ErrorCode.status(): HttpStatus`, `ErrorCode.code(): String`, `ErrorCode.message(): String`.
- Produces: `ApiResponse.success(T)`, `ApiResponse.success(String, String, T)`, `ApiResponse.error(ErrorCode)`, and `ApiResponse.error(ErrorCode, T)`.
- Produces: `ValidationError(String field, String message)`.

- [ ] **Step 1: Write the failing response factory tests**

```java
package com.ssafy.upcycle.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseTests {

    @Test
    void createsDefaultSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("SUCCESS");
        assertThat(response.getMessage()).isEqualTo("요청에 성공했습니다.");
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void createsCustomSuccessResponse() {
        ApiResponse<Long> response = ApiResponse.success("CREATED", "생성되었습니다.", 1L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("CREATED");
        assertThat(response.getMessage()).isEqualTo("생성되었습니다.");
        assertThat(response.getData()).isEqualTo(1L);
    }

    @Test
    void createsErrorResponseWithAndWithoutDetails() {
        ApiResponse<Void> withoutDetails = ApiResponse.error(CommonErrorCode.RESOURCE_NOT_FOUND);
        ValidationError detail = new ValidationError("name", "이름은 필수입니다.");
        ApiResponse<ValidationError> withDetails =
                ApiResponse.error(CommonErrorCode.VALIDATION_ERROR, detail);

        assertThat(withoutDetails.isSuccess()).isFalse();
        assertThat(withoutDetails.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(withoutDetails.getData()).isNull();
        assertThat(withDetails.isSuccess()).isFalse();
        assertThat(withDetails.getData()).isEqualTo(detail);
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=ApiResponseTests test
```

Expected: compilation fails because `ApiResponse`, `ValidationError`, and the error-code types do not exist.

- [ ] **Step 3: Add the error code interface and common catalog**

```java
package com.ssafy.upcycle.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    HttpStatus status();

    String code();

    String message();
}
```

```java
package com.ssafy.upcycle.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값 검증에 실패했습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CommonErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
```

- [ ] **Step 4: Add immutable response DTOs and factories**

```java
package com.ssafy.upcycle.common.response;

import com.ssafy.upcycle.common.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public final class ApiResponse<T> {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String SUCCESS_MESSAGE = "요청에 성공했습니다.";

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;

    private ApiResponse(
            boolean success, String code, String message, T data, LocalDateTime timestamp) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data, LocalDateTime.now());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(
                false, errorCode.code(), errorCode.message(), data, LocalDateTime.now());
    }
}
```

```java
package com.ssafy.upcycle.common.response;

public record ValidationError(String field, String message) {
}
```

- [ ] **Step 5: Run the focused test and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=ApiResponseTests test
```

Expected: 3 tests pass with no failures.

- [ ] **Step 6: Commit the response contract**

```powershell
git add src/main/java/com/ssafy/upcycle/common/exception/ErrorCode.java src/main/java/com/ssafy/upcycle/common/exception/CommonErrorCode.java src/main/java/com/ssafy/upcycle/common/response/ApiResponse.java src/main/java/com/ssafy/upcycle/common/response/ValidationError.java src/test/java/com/ssafy/upcycle/common/response/ApiResponseTests.java
git commit -m "feat: 공통 API 응답 형식 추가"
```

---

### Task 2: Business and Validation Exception Handling

**Files:**
- Create: `src/main/java/com/ssafy/upcycle/common/exception/BusinessException.java`
- Create: `src/main/java/com/ssafy/upcycle/common/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/ssafy/upcycle/common/exception/GlobalExceptionHandlerTests.java`

**Interfaces:**
- Consumes: `ErrorCode` and `ApiResponse` factories from Task 1.
- Produces: `new BusinessException(ErrorCode)` and `BusinessException.getErrorCode()`.
- Produces: `GlobalExceptionHandler` mappings for business, request-body validation, and constraint validation errors.

- [ ] **Step 1: Write failing MockMvc tests for business and request validation errors**

Create `GlobalExceptionHandlerTests` with this initial content:

```java
package com.ssafy.upcycle.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTests {

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void handlesBusinessException() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void handlesBeanValidationErrorsWithoutRejectedValue() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("name"))
                .andExpect(jsonPath("$.data[0].message").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.data[0].rejectedValue").doesNotExist());
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business")
        ApiResponse<Void> business() {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }

        @PostMapping("/validation")
        ApiResponse<Void> validation(@Valid @RequestBody TestRequest request) {
            return ApiResponse.success(null);
        }
    }

    record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
    }
}
```

- [ ] **Step 2: Run the handler test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=GlobalExceptionHandlerTests test
```

Expected: compilation fails because `BusinessException` and `GlobalExceptionHandler` do not exist.

- [ ] **Step 3: Implement `BusinessException`**

```java
package com.ssafy.upcycle.common.exception;

import java.util.Objects;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").message());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

- [ ] **Step 4: Implement business and validation handlers**

Create `GlobalExceptionHandler` with imports for Spring MVC exceptions, Jakarta validation, logging, collections, and the response DTOs. Add these methods and helpers:

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("Business exception: code={}, message={}", errorCode.code(), errorCode.message());
        return ResponseEntity.status(errorCode.status()).body(ApiResponse.error(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<ValidationError>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> Objects.requireNonNullElse(
                                error.getDefaultMessage(), "잘못된 값입니다."),
                        (left, right) -> left.compareTo(right) <= 0 ? left : right,
                        TreeMap::new))
                .entrySet().stream()
                .map(entry -> new ValidationError(entry.getKey(), entry.getValue()))
                .toList();
        log.warn("Request validation failed: fields={}",
                errors.stream().map(ValidationError::field).toList());
        return validationError(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<ValidationError>>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        List<ValidationError> errors = exception.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> leafField(violation.getPropertyPath().toString()),
                        ConstraintViolation::getMessage,
                        (left, right) -> left.compareTo(right) <= 0 ? left : right,
                        TreeMap::new))
                .entrySet().stream()
                .map(entry -> new ValidationError(entry.getKey(), entry.getValue()))
                .toList();
        log.warn("Constraint validation failed: fields={}",
                errors.stream().map(ValidationError::field).toList());
        return validationError(errors);
    }

    private ResponseEntity<ApiResponse<List<ValidationError>>> validationError(
            List<ValidationError> errors) {
        CommonErrorCode errorCode = CommonErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(errorCode.status()).body(ApiResponse.error(errorCode, errors));
    }

    private String leafField(String propertyPath) {
        int separatorIndex = propertyPath.lastIndexOf('.');
        return separatorIndex < 0 ? propertyPath : propertyPath.substring(separatorIndex + 1);
    }
}
```

Required imports include:

```java
import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.response.ValidationError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
```

- [ ] **Step 5: Add a direct constraint-validation handler test**

Add imports for `ConstraintViolation`, `ConstraintViolationException`, `Set`, `ResponseEntity`, and the response DTOs, then add this test and record to `GlobalExceptionHandlerTests`:

```java
@Test
void handlesConstraintViolations() {
    ConstraintTarget target = new ConstraintTarget("");
    Set<ConstraintViolation<ConstraintTarget>> violations = validator.validate(target);
    ConstraintViolationException exception = new ConstraintViolationException(violations);

    ResponseEntity<ApiResponse<List<ValidationError>>> response =
            new GlobalExceptionHandler().handleConstraintViolationException(exception);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().getData())
            .containsExactly(new ValidationError("value", "값은 필수입니다."));
}

record ConstraintTarget(@NotBlank(message = "값은 필수입니다.") String value) {
}
```

Also add:

```java
import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.response.ValidationError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
```

- [ ] **Step 6: Run the focused handler tests and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=GlobalExceptionHandlerTests test
```

Expected: 3 tests pass with no failures.

- [ ] **Step 7: Commit expected exception handling**

```powershell
git add src/main/java/com/ssafy/upcycle/common/exception/BusinessException.java src/main/java/com/ssafy/upcycle/common/exception/GlobalExceptionHandler.java src/test/java/com/ssafy/upcycle/common/exception/GlobalExceptionHandlerTests.java
git commit -m "feat: 비즈니스 및 검증 예외 처리 추가"
```

---

### Task 3: Framework Errors, Unexpected Errors, and Full Verification

**Files:**
- Modify: `src/main/java/com/ssafy/upcycle/common/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/ssafy/upcycle/common/exception/GlobalExceptionHandlerTests.java`

**Interfaces:**
- Consumes: `CommonErrorCode` and `ApiResponse.error(ErrorCode)`.
- Produces: handler mappings for malformed JSON, unsupported methods, missing resources, and unexpected exceptions.

- [ ] **Step 1: Add failing MockMvc tests for malformed JSON and unexpected errors**

Add this endpoint to `TestController`:

```java
@GetMapping("/unexpected")
ApiResponse<Void> unexpected() {
    throw new IllegalStateException("internal detail");
}
```

Add these tests:

```java
@Test
void handlesMalformedJson() throws Exception {
    mockMvc.perform(post("/test/validation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.data").doesNotExist());
}

@Test
void masksUnexpectedExceptionDetails() throws Exception {
String body = mockMvc.perform(get("/test/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
        .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
        .andReturn().getResponse().getContentAsString();

assertThat(body).doesNotContain("internal detail", "IllegalStateException");
}
```

- [ ] **Step 2: Add failing direct tests for method and resource errors**

```java
@Test
void handlesUnsupportedMethod() {
    HttpRequestMethodNotSupportedException exception =
            new HttpRequestMethodNotSupportedException("PATCH", List.of("GET"));

    ResponseEntity<ApiResponse<Void>> response =
            new GlobalExceptionHandler().handleMethodNotSupportedException(exception);

    assertThat(response.getStatusCode().value()).isEqualTo(405);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getCode()).isEqualTo("METHOD_NOT_ALLOWED");
}

@Test
void handlesMissingResource() {
    NoResourceFoundException exception =
            new NoResourceFoundException(HttpMethod.GET, "/missing");

    ResponseEntity<ApiResponse<Void>> response =
            new GlobalExceptionHandler().handleNoResourceFoundException(exception);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
}
```

Add imports:

```java
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
```

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=GlobalExceptionHandlerTests test
```

Expected: the new tests fail because the four handler methods are missing; unexpected errors may propagate from MockMvc.

- [ ] **Step 4: Implement framework and unexpected exception handlers**

Add these methods to `GlobalExceptionHandler`:

```java
@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
        HttpMessageNotReadableException exception) {
    log.warn("Request body is not readable");
    return error(CommonErrorCode.INVALID_REQUEST);
}

@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(
        HttpRequestMethodNotSupportedException exception) {
    log.warn("HTTP method is not supported: method={}", exception.getMethod());
    return error(CommonErrorCode.METHOD_NOT_ALLOWED);
}

@ExceptionHandler(NoResourceFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
        NoResourceFoundException exception) {
    log.warn("Resource not found: path={}", exception.getResourcePath());
    return error(CommonErrorCode.RESOURCE_NOT_FOUND);
}

@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
    log.error("Unexpected exception", exception);
    return error(CommonErrorCode.INTERNAL_SERVER_ERROR);
}

private ResponseEntity<ApiResponse<Void>> error(ErrorCode errorCode) {
    return ResponseEntity.status(errorCode.status()).body(ApiResponse.error(errorCode));
}
```

Add imports:

```java
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
```

- [ ] **Step 5: Run all common response and exception tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ApiResponseTests,GlobalExceptionHandlerTests test
```

Expected: every common response and exception test passes.

- [ ] **Step 6: Run the full verification suite**

Run:

```powershell
.\mvnw.cmd clean verify
```

Expected: the new tests and all 13 pre-existing tests pass, the executable JAR is built, and Maven reports `BUILD SUCCESS`.

- [ ] **Step 7: Inspect the final diff and secret exposure**

Run:

```powershell
git diff --check
rg -n "rejectedValue|printStackTrace|exception\.getMessage\(\)" src/main/java/com/ssafy/upcycle/common
git status --short
```

Expected: `git diff --check` reports no errors; production response code contains no rejected-value serialization, `printStackTrace`, or exception-message exposure; only the planned files are modified.

- [ ] **Step 8: Commit completed global handling**

```powershell
git add src/main/java/com/ssafy/upcycle/common/exception/GlobalExceptionHandler.java src/test/java/com/ssafy/upcycle/common/exception/GlobalExceptionHandlerTests.java
git commit -m "feat: 공통 예외 처리 완성"
```
