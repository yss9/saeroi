# Common API Response and Exception Design

## Goal

Provide one predictable JSON envelope for successful and failed REST API responses, and centralize exception-to-response conversion. Domain packages must be able to define their own error codes without adding every domain error to the `common` package.

## Scope

This change adds the shared response contract, the shared exception contract, common error codes, and a global REST exception handler. It does not add domain controllers, domain-specific error enums, localization, distributed tracing, or persistence-related exception translation.

## Response Contract

All application responses use `ApiResponse<T>` with these fields:

- `success`: whether the request succeeded
- `code`: a stable machine-readable code
- `message`: a human-readable Korean message
- `data`: the response payload, validation details, or `null`
- `timestamp`: the response creation time in ISO-8601 format

A successful response has HTTP status `2xx`, `success: true`, and the default code `SUCCESS`.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  },
  "timestamp": "2026-08-28T17:30:00"
}
```

An error response uses the HTTP status declared by its error code and sets `success: false`.

```json
{
  "success": false,
  "code": "RESOURCE_NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-08-28T17:30:00"
}
```

`ApiResponse<T>` exposes named factory methods instead of public constructors:

- `success(T data)` for the default success response
- `success(String code, String message, T data)` for a specific success result
- `error(ErrorCode errorCode)` for an error without details
- `error(ErrorCode errorCode, T data)` for an error with safe client-facing details

## Error Code Contract

`ErrorCode` is an interface with three properties:

- `HttpStatus status`
- `String code`
- `String message`

`CommonErrorCode` implements this interface and initially defines:

| Error code | HTTP status | Purpose |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | Malformed JSON or an invalid request structure |
| `VALIDATION_ERROR` | 400 | Bean Validation failure |
| `RESOURCE_NOT_FOUND` | 404 | Missing resource or unmapped resource request |
| `METHOD_NOT_ALLOWED` | 405 | Unsupported HTTP method |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected server failure |

Each domain can add an enum such as `UserErrorCode` or `TradeErrorCode` that implements `ErrorCode`. This keeps domain policy in its owning package while allowing the common handler to process every domain consistently.

## Exception Model

`BusinessException` is the common runtime exception for expected application failures. It contains one `ErrorCode`; the handler derives the HTTP status, response code, and client message from that value. The initial version does not accept arbitrary client messages, preventing accidental exposure of internal values and keeping the public error contract stable.

Expected failures are logged at warning level without a stack trace. Unexpected exceptions are logged at error level with their stack trace, while the client receives only `INTERNAL_SERVER_ERROR`.

## Global Exception Handling

`GlobalExceptionHandler` uses `@RestControllerAdvice` and maps exceptions as follows:

| Exception | Result |
| --- | --- |
| `BusinessException` | The contained `ErrorCode` |
| `MethodArgumentNotValidException` | `VALIDATION_ERROR` with field errors |
| `ConstraintViolationException` | `VALIDATION_ERROR` with constraint errors |
| `HandlerMethodValidationException` | `VALIDATION_ERROR` with native MVC method-parameter errors |
| `HttpMessageNotReadableException` | `INVALID_REQUEST` |
| `HttpRequestMethodNotSupportedException` | `METHOD_NOT_ALLOWED` |
| `NoResourceFoundException` | `RESOURCE_NOT_FOUND` |
| Any other `Exception` | `INTERNAL_SERVER_ERROR` |

Validation failures place a list of `ValidationError` values in `data`. Each item contains only `field` and `message`. Rejected values are excluded because they may contain credentials or other sensitive input.

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "입력값 검증에 실패했습니다.",
  "data": [
    {
      "field": "name",
      "message": "이름은 필수입니다."
    }
  ],
  "timestamp": "2026-08-28T17:30:00"
}
```

When multiple validation failures target the same field, the handler retains the lexicographically smallest message for that field. The result is sorted by field name so tests and clients receive stable output regardless of validation-error encounter order.

## Package Structure

```text
com.ssafy.upcycle.common
├── exception
│   ├── BusinessException.java
│   ├── CommonErrorCode.java
│   ├── ErrorCode.java
│   └── GlobalExceptionHandler.java
└── response
    ├── ApiResponse.java
    └── ValidationError.java
```

`response` owns serialized API DTOs. `exception` owns error definitions and translation into those DTOs. Neither package depends on a domain package.

## Data Flow

For success, a controller creates an `ApiResponse` using a success factory and Spring serializes it normally.

For an expected failure, domain logic throws `BusinessException` with a domain or common `ErrorCode`. `GlobalExceptionHandler` converts it into an `ApiResponse` and an HTTP status. Framework request errors follow the same handler path. Unexpected errors are logged internally and converted to the generic server error response.

## Testing

Implementation follows test-driven development. Tests cover:

1. Default and custom success response factories.
2. Error response factories with and without safe detail data.
3. `BusinessException` status and body mapping.
4. Bean Validation field-error serialization without rejected values.
5. Malformed request, unsupported method, and missing resource mappings.
6. Unexpected exception masking as `INTERNAL_SERVER_ERROR`.
7. The full Maven verification suite to protect the existing package and JPA tests.

The implementation is complete when the new response and handler tests pass, all existing tests remain green, and the project builds with `mvnw clean verify`.
