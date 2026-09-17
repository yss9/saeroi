package com.ssafy.upcycle.common.exception;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.storage.FileStorageErrorCode;
import com.ssafy.upcycle.common.response.ValidationError;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(
            BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("Business exception: code={}, message={}", errorCode.code(), errorCode.message());
        // 상세가 없으면 지금까지와 똑같이 data 가 null 인 봉투가 나간다.
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.error(errorCode, exception.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<ValidationError>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        List<ValidationError> errors = validationErrors(
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> new ValidationError(
                                error.getField(), validationMessage(error.getDefaultMessage()))));
        log.warn("Request validation failed: fields={}",
                errors.stream().map(ValidationError::field).toList());
        return validationError(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<ValidationError>>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        List<ValidationError> errors = validationErrors(
                exception.getConstraintViolations().stream()
                        .map(violation -> new ValidationError(
                                leafField(violation.getPropertyPath().toString()),
                                validationMessage(violation.getMessage()))));
        log.warn("Constraint validation failed: fields={}",
                errors.stream().map(ValidationError::field).toList());
        return validationError(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<List<ValidationError>>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception) {
        List<ValidationError> errors = validationErrors(
                exception.getParameterValidationResults().stream()
                        .flatMap(this::parameterValidationErrors));
        log.warn("Method validation failed: fields={}",
                errors.stream().map(ValidationError::field).toList());
        return validationError(errors);
    }

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

    /**
     * 업로드 크기 초과.
     *
     * <p><b>번역하지 않으면 500 이 나간다.</b> Spring 의 {@code max-file-size} /
     * {@code max-request-size} 는 우리 검증에 닿기 <b>전에</b> 걸러내므로,
     * {@code EvidenceUploadService} 의 {@code FILE_TOO_LARGE} 검사가 실행되지 않는다.
     * 사용자에게는 "서버 오류" 로 보이고, 사진을 줄이면 된다는 것을 알 방법이 없다.
     *
     * <p>파일 하나 초과와 요청 전체 초과를 구분하지 않는다. Spring 이 두 경우에 같은
     * 예외를 던지고, 사용자가 할 일도 같다 — 파일을 줄이거나 나눠 올린다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception) {
        log.warn("Upload exceeded the configured limit: max={}", exception.getMaxUploadSize());
        return error(FileStorageErrorCode.FILE_TOO_LARGE);
    }

    /**
     * 경로·쿼리 파라미터의 타입 변환 실패.
     *
     * <p><b>번역하지 않으면 500 이 나간다.</b> {@code /brands/{id}} 의 {@code id} 가 Long 인데
     * {@code 1e10} · {@code 99999999999999999999}(overflow) · {@code 1 OR 1=1} 처럼 숫자로
     * 바뀌지 않는 값이 오면 Spring 이 이 예외를 던지고, 아무도 잡지 않으면 catch-all 로
     * 떨어져 <b>ERROR 로그 + 500</b> 이 된다. 이건 클라이언트가 잘못된 값을 보낸 것이므로
     * 400 이어야 하고, permitAll 경로(public 카탈로그·QR)에서 인증 없이도 유발되므로 —
     * 그대로 두면 아무나 ERROR 로그를 무한히 채울 수 있다. (2026-09-17 실측)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
        log.warn("Argument type mismatch: name={}, value={}",
                exception.getName(), exception.getValue());
        return error(CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 지원하지 않는 요청 Content-Type.
     *
     * <p><b>번역하지 않으면 500 이 나간다.</b> JSON 을 기대하는 엔드포인트에 {@code text/plain}
     * 이나 Content-Type 없는 본문이 오면 Spring 이 이 예외를 던지는데, 잡지 않으면 catch-all
     * 로 떨어져 500 이 된다. 클라이언트의 헤더 실수이므로 415 가 맞다. (2026-09-17 실측)
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception) {
        log.warn("Unsupported media type: contentType={}", exception.getContentType());
        return error(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unexpected exception", exception);
        return error(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> error(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status()).body(ApiResponse.error(errorCode));
    }

    private ResponseEntity<ApiResponse<List<ValidationError>>> validationError(
            List<ValidationError> errors) {
        CommonErrorCode errorCode = CommonErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(errorCode.status()).body(ApiResponse.error(errorCode, errors));
    }

    private List<ValidationError> validationErrors(Stream<ValidationError> errors) {
        return errors.collect(Collectors.toMap(
                        ValidationError::field,
                        ValidationError::message,
                        (left, right) -> left.compareTo(right) <= 0 ? left : right,
                        TreeMap::new))
                .entrySet().stream()
                .map(entry -> new ValidationError(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Stream<ValidationError> parameterValidationErrors(
            ParameterValidationResult result) {
        if (result instanceof ParameterErrors errors && errors.hasFieldErrors()) {
            return errors.getFieldErrors().stream()
                    .map(error -> new ValidationError(
                            error.getField(), validationMessage(error.getDefaultMessage())));
        }

        String field = methodParameterField(result);
        return result.getResolvableErrors().stream()
                .map(error -> new ValidationError(
                        field, validationMessage(error.getDefaultMessage())));
    }

    private String methodParameterField(ParameterValidationResult result) {
        RequestParam requestParam =
                result.getMethodParameter().getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isBlank()) {
            return requestParam.name();
        }

        return Objects.requireNonNullElse(
                result.getMethodParameter().getParameterName(),
                "arg" + result.getMethodParameter().getParameterIndex());
    }

    private String validationMessage(String message) {
        return Objects.requireNonNullElse(message, "잘못된 값입니다.");
    }

    private String leafField(String propertyPath) {
        int separatorIndex = propertyPath.lastIndexOf('.');
        return separatorIndex < 0 ? propertyPath : propertyPath.substring(separatorIndex + 1);
    }
}
