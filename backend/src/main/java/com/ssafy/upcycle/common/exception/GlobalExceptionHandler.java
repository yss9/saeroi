package com.ssafy.upcycle.common.exception;

import com.ssafy.upcycle.common.response.ApiResponse;
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
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
