package com.fabricmanagement.common.infrastructure.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError handleNotFound(NotFoundException ex, HttpServletRequest req) {
    log.info("Resource not found: {}", ex.getMessage());
    return ApiError.of(404, "Not Found", "NOT_FOUND", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiError> handleDomain(DomainException ex, HttpServletRequest req) {
    log.info("Domain rule violation [{}]: {}", ex.getErrorCode(), ex.getMessage());
    ApiError error =
        new ApiError(
            java.time.Instant.now(),
            ex.getHttpStatus(),
            HttpStatus.valueOf(ex.getHttpStatus()).getReasonPhrase(),
            ex.getErrorCode(),
            ex.getArgs(),
            ex.getMessage(),
            req.getRequestURI(),
            ex.getDetails());
    return ResponseEntity.status(ex.getHttpStatus()).body(error);
  }

  @ExceptionHandler(TaxIdAlreadyExistsException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleTaxIdAlreadyExists(TaxIdAlreadyExistsException ex, HttpServletRequest req) {
    log.info("Signup conflict (tax ID): {}", ex.getMessage());
    return ApiError.of(
        400, "Bad Request", "TAX_ID_ALREADY_EXISTS", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(ContactAlreadyRegisteredException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleContactAlreadyRegistered(
      ContactAlreadyRegisteredException ex, HttpServletRequest req) {
    log.info("Signup conflict (contact): {}", ex.getMessage());
    return ApiError.of(
        400, "Bad Request", "CONTACT_ALREADY_REGISTERED", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError handleEntityNotFound(
      jakarta.persistence.EntityNotFoundException ex, HttpServletRequest req) {
    log.info("Entity not found: {}", ex.getMessage());
    return ApiError.of(404, "Not Found", "ENTITY_NOT_FOUND", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleIllegal(RuntimeException ex, HttpServletRequest req) {
    log.info("Illegal argument/state: {}", ex.getMessage());
    return ApiError.of(
        400, "Bad Request", "ILLEGAL_ARGUMENT", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
    Map<String, String> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    fieldError ->
                        fieldError.getDefaultMessage() != null
                            ? fieldError.getDefaultMessage()
                            : "",
                    (existing, replacement) -> existing));

    log.info("Validation failed: {}", fieldErrors);
    return ApiError.of(
        400,
        "Bad Request",
        "VALIDATION_ERROR",
        "Validation failed",
        req.getRequestURI(),
        fieldErrors);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    Map<String, String> details =
        ex.getConstraintViolations().stream()
            .collect(
                Collectors.toMap(
                    v -> v.getPropertyPath().toString(),
                    ConstraintViolation::getMessage,
                    (existing, replacement) -> existing));

    log.info("Constraint violation: {}", details);
    return ApiError.of(
        400,
        "Bad Request",
        "CONSTRAINT_VIOLATION",
        "Validation failed",
        req.getRequestURI(),
        details);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
    log.info("Request body not readable: {}", ex.getMessage());
    return ApiError.of(
        400, "Bad Request", "BAD_REQUEST", "Request body is not readable", req.getRequestURI());
  }

  @ExceptionHandler({
    ObjectOptimisticLockingFailureException.class,
    StaleObjectStateException.class
  })
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiError handleOptimisticLock(Exception ex, HttpServletRequest req) {
    log.warn("JPA Optimistic lock error on {}: {}", req.getRequestURI(), ex.getMessage());
    return ApiError.of(
        HttpStatus.CONFLICT.value(),
        "Conflict",
        "OPTIMISTIC_LOCK",
        "This record was modified by another user. Please refresh and try again.",
        req.getRequestURI());
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiError handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
    log.warn("Access denied: {} — {}", req.getRequestURI(), ex.getMessage());
    return ApiError.of(403, "Forbidden", "ACCESS_DENIED", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(TooManyRequestsException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public ApiError handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest req) {
    log.warn("Rate limit exceeded: {}", ex.getMessage());
    return ApiError.of(
        429, "Too Many Requests", "RATE_LIMIT_EXCEEDED", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiError handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
    log.error("Data integrity violation: {}", ex.getMessage(), ex);
    String msg = "Data conflict (unique or FK constraint).";
    return ApiError.of(409, "Conflict", "DATA_INTEGRITY", msg, req.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
    String paramName = ex.getName();
    String requiredType =
        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
    String message = String.format("Parameter '%s' must be a valid %s", paramName, requiredType);

    // Truncate value in logs to avoid leaking sensitive data
    Object rawValue = ex.getValue();
    String safeValue =
        rawValue != null
            ? rawValue.toString().substring(0, Math.min(rawValue.toString().length(), 36))
            : "null";
    log.info("Type mismatch: param={}, required={}, value={}", paramName, requiredType, safeValue);

    return ApiError.of(400, "Bad Request", "TYPE_MISMATCH", message, req.getRequestURI());
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiError handleGeneric(Exception ex, HttpServletRequest req) {
    log.error("Unexpected error occurred: {}", req.getRequestURI(), ex);
    return ApiError.of(
        500,
        "Internal Server Error",
        "UNEXPECTED_ERROR",
        "An unexpected error occurred. Please contact support.",
        req.getRequestURI());
  }
}
