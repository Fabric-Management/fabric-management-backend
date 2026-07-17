package com.fabricmanagement.common.infrastructure.web.exception;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

  private final org.springframework.beans.factory.ObjectProvider<MeterRegistry>
      meterRegistryProvider;

  private ApiProblemDetail buildProblemDetail(
      HttpStatus status, String title, String code, String detail, String uri) {
    ApiProblemDetail pd = ApiProblemDetail.forStatusAndDetail(status, detail);
    pd.setTitle(title);
    pd.setCode(code);
    pd.setInstance(URI.create(uri));
    return pd;
  }

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest req) {
    log.info("Resource not found: {}", ex.getMessage());
    return buildProblemDetail(
        HttpStatus.NOT_FOUND, "Not Found", "NOT_FOUND", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiProblemDetail> handleDomain(DomainException ex, HttpServletRequest req) {
    log.info("Domain rule violation [{}]: {}", ex.getErrorCode(), ex.getMessage());
    HttpStatus status = HttpStatus.valueOf(ex.getHttpStatus());
    ApiProblemDetail pd =
        buildProblemDetail(
            status,
            status.getReasonPhrase(),
            ex.getErrorCode(),
            ex.getMessage(),
            req.getRequestURI());
    pd.setArgs(ex.getArgs());
    if (ex.getDetails() != null && !ex.getDetails().isEmpty()) {
      ex.getDetails().forEach(pd::setProperty);
    }
    return ResponseEntity.status(status).body(pd);
  }

  @ExceptionHandler(TenantReadOnlyException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiProblemDetail handleTenantReadOnly(TenantReadOnlyException ex, HttpServletRequest req) {
    log.info("Read-only tenant write rejected: {}", req.getRequestURI());
    return buildProblemDetail(
        HttpStatus.FORBIDDEN,
        "Forbidden",
        TenantReadOnlyException.CODE,
        ex.getMessage(),
        req.getRequestURI());
  }

  @ExceptionHandler(TaxIdAlreadyExistsException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiProblemDetail handleTaxIdAlreadyExists(
      TaxIdAlreadyExistsException ex, HttpServletRequest req) {
    log.info("Signup conflict (tax ID): {}", ex.getMessage());
    return buildProblemDetail(
        HttpStatus.BAD_REQUEST,
        "Bad Request",
        "TAX_ID_ALREADY_EXISTS",
        ex.getMessage(),
        req.getRequestURI());
  }

  @ExceptionHandler(ContactAlreadyRegisteredException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiProblemDetail handleContactAlreadyRegistered(
      ContactAlreadyRegisteredException ex, HttpServletRequest req) {
    log.info("Signup conflict (contact): {}", ex.getMessage());
    return buildProblemDetail(
        HttpStatus.BAD_REQUEST,
        "Bad Request",
        "CONTACT_ALREADY_REGISTERED",
        ex.getMessage(),
        req.getRequestURI());
  }

  @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiProblemDetail handleEntityNotFound(
      jakarta.persistence.EntityNotFoundException ex, HttpServletRequest req) {
    log.info("Entity not found: {}", ex.getMessage());
    return buildProblemDetail(
        HttpStatus.NOT_FOUND,
        "Not Found",
        "ENTITY_NOT_FOUND",
        ex.getMessage(),
        req.getRequestURI());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiProblemDetail handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest req) {
    log.info("Illegal argument: {}", ex.getMessage());
    return buildProblemDetail(
        HttpStatus.BAD_REQUEST,
        "Bad Request",
        "ILLEGAL_ARGUMENT",
        ex.getMessage(),
        req.getRequestURI());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiProblemDetail handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
    log.info("Illegal state: {}", ex.getMessage());
    return buildProblemDetail(
        HttpStatus.CONFLICT, "Conflict", "ILLEGAL_STATE", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ApiProblemDetail handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
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
    ApiProblemDetail pd =
        buildProblemDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Unprocessable Entity",
            "VALIDATION_ERROR",
            "Validation failed",
            req.getRequestURI());
    pd.setErrors(fieldErrors);
    return pd;
  }

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ApiProblemDetail handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    Map<String, String> details =
        ex.getConstraintViolations().stream()
            .collect(
                Collectors.toMap(
                    v -> v.getPropertyPath().toString(),
                    ConstraintViolation::getMessage,
                    (existing, replacement) -> existing));

    log.info("Constraint violation: {}", details);
    ApiProblemDetail pd =
        buildProblemDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Unprocessable Entity",
            "CONSTRAINT_VIOLATION",
            "Validation failed",
            req.getRequestURI());
    pd.setErrors(details);
    return pd;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiProblemDetail handleNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest req) {
    log.info("Request body not readable: {}", ex.getMessage());
    return buildProblemDetail(
        HttpStatus.BAD_REQUEST,
        "Bad Request",
        "BAD_REQUEST",
        "Request body is not readable",
        req.getRequestURI());
  }

  @ExceptionHandler({
    OptimisticLockException.class,
    ObjectOptimisticLockingFailureException.class,
    StaleObjectStateException.class
  })
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiProblemDetail handleOptimisticLock(Exception ex, HttpServletRequest req) {
    log.warn("JPA Optimistic lock error on {}: {}", req.getRequestURI(), ex.getMessage());
    return buildProblemDetail(
        HttpStatus.CONFLICT,
        "Conflict",
        "OPTIMISTIC_LOCK",
        "This record was modified by another user. Please refresh and try again.",
        req.getRequestURI());
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
    log.warn("Access denied: {} — {}", req.getRequestURI(), ex.getMessage());
    meterRegistryProvider.ifAvailable(
        registry ->
            registry.counter("security.access.denied", "uri", req.getRequestURI()).increment());
    return buildProblemDetail(
        HttpStatus.FORBIDDEN, "Forbidden", "ACCESS_DENIED", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(TooManyRequestsException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public ApiProblemDetail handleTooManyRequests(
      TooManyRequestsException ex, HttpServletRequest req) {
    log.warn("Rate limit exceeded: {}", ex.getMessage());
    return buildProblemDetail(
        HttpStatus.TOO_MANY_REQUESTS,
        "Too Many Requests",
        "RATE_LIMIT_EXCEEDED",
        ex.getMessage(),
        req.getRequestURI());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiProblemDetail handleIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest req) {
    log.error("Data integrity violation: {}", ex.getMessage(), ex);
    String msg = "Data conflict (unique or FK constraint).";
    return buildProblemDetail(
        HttpStatus.CONFLICT, "Conflict", "DATA_INTEGRITY", msg, req.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiProblemDetail handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
    String paramName = ex.getName();
    String requiredType =
        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
    String message = String.format("Parameter '%s' must be a valid %s", paramName, requiredType);

    Object rawValue = ex.getValue();
    String safeValue =
        rawValue != null
            ? rawValue.toString().substring(0, Math.min(rawValue.toString().length(), 36))
            : "null";
    log.info("Type mismatch: param={}, required={}, value={}", paramName, requiredType, safeValue);

    return buildProblemDetail(
        HttpStatus.BAD_REQUEST, "Bad Request", "TYPE_MISMATCH", message, req.getRequestURI());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiProblemDetail> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
    log.info("HTTP method not supported: {} — {}", req.getRequestURI(), ex.getMethod());
    ApiProblemDetail problemDetail =
        buildProblemDetail(
            HttpStatus.METHOD_NOT_ALLOWED,
            "Method Not Allowed",
            "METHOD_NOT_ALLOWED",
            ex.getMessage(),
            req.getRequestURI());

    ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
    Set<HttpMethod> supportedMethods = ex.getSupportedHttpMethods();
    if (supportedMethods != null) {
      response.allow(supportedMethods.toArray(HttpMethod[]::new));
    }
    return response.body(problemDetail);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiProblemDetail handleGeneric(Exception ex, HttpServletRequest req) {
    log.error("Unexpected error occurred: {}", req.getRequestURI(), ex);
    return buildProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal Server Error",
        "UNEXPECTED_ERROR",
        "An unexpected error occurred. Please contact support.",
        req.getRequestURI());
  }
}
