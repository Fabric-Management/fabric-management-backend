package com.fabricmanagement.common.infrastructure.web.exception;

import com.fabricmanagement.common.platform.subscription.domain.exception.FeatureNotAvailableException;
import com.fabricmanagement.common.platform.subscription.domain.exception.QuotaExceededException;
import com.fabricmanagement.common.platform.subscription.domain.exception.SubscriptionRequiredException;
import com.fabricmanagement.production.common.exception.ForbiddenOperationException;
import com.fabricmanagement.production.common.exception.InsufficientStockException;
import com.fabricmanagement.production.common.exception.InvalidStatusTransitionException;
import com.fabricmanagement.production.common.exception.OptimisticLockConflictException;
import com.fabricmanagement.production.common.exception.ProductionDomainException;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.RecipeInUseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Global exception handler for all controllers. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  // ---------------------------------------------------------------------------
  // Production domain exceptions
  // Order matters: specific subclasses must be declared BEFORE the base class.
  // ---------------------------------------------------------------------------

  @ExceptionHandler(InsufficientStockException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ApiError handleInsufficientStock(InsufficientStockException ex, HttpServletRequest req) {
    log.info("Insufficient stock: {}", ex.getMessage());
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("batchId", ex.getBatchId());
    details.put("requested", ex.getRequested());
    details.put("available", ex.getAvailable());
    details.put("unit", ex.getUnit());
    return ApiError.of(
        422,
        "Unprocessable Entity",
        "INSUFFICIENT_STOCK",
        ex.getMessage(),
        req.getRequestURI(),
        details);
  }

  @ExceptionHandler(InvalidStatusTransitionException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiError handleInvalidStatusTransition(
      InvalidStatusTransitionException ex, HttpServletRequest req) {
    log.info("Invalid status transition: {}", ex.getMessage());
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("entityType", ex.getEntityType());
    details.put("from", ex.getFrom());
    details.put("to", ex.getTo());
    return ApiError.of(
        409,
        "Conflict",
        "INVALID_STATUS_TRANSITION",
        ex.getMessage(),
        req.getRequestURI(),
        details);
  }

  @ExceptionHandler(RecipeInUseException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiError handleRecipeInUse(RecipeInUseException ex, HttpServletRequest req) {
    log.info("Recipe in use — fiber={}, id={}", ex.getFiberName(), ex.getFiberId());
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("fiberId", ex.getFiberId());
    details.put("fiberName", ex.getFiberName());
    details.put("blockedBy", "RESERVED, IN_PROGRESS");
    return ApiError.of(
        409, "Conflict", "RECIPE_IN_USE", ex.getMessage(), req.getRequestURI(), details);
  }

  @ExceptionHandler(ProductionDomainException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleProductionDomain(ProductionDomainException ex, HttpServletRequest req) {
    log.info("Production rule violation: {}", ex.getMessage());
    return ApiError.of(
        400, "Bad Request", "PRODUCTION_RULE_VIOLATION", ex.getMessage(), req.getRequestURI());
  }

  // ---------------------------------------------------------------------------
  // Subscription / quota exceptions
  // ---------------------------------------------------------------------------

  @ExceptionHandler(SubscriptionRequiredException.class)
  @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
  public ApiError handleSubscriptionRequired(
      SubscriptionRequiredException ex, HttpServletRequest req) {
    log.info(
        "Subscription required: requiredOs={}, path={}", ex.getRequiredOs(), req.getRequestURI());
    Map<String, Object> details = new LinkedHashMap<>();
    if (ex.getRequiredOs() != null) {
      details.put("requiredOs", ex.getRequiredOs());
      details.put("upgradeUrl", "/subscriptions/add/" + ex.getRequiredOs());
    }
    return ApiError.of(
        402,
        "Payment Required",
        "SUBSCRIPTION_REQUIRED",
        ex.getMessage(),
        req.getRequestURI(),
        details);
  }

  @ExceptionHandler(FeatureNotAvailableException.class)
  @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
  public ApiError handleFeatureNotAvailable(
      FeatureNotAvailableException ex, HttpServletRequest req) {
    log.info(
        "Feature not available: featureId={}, minimumTier={}",
        ex.getFeatureId(),
        ex.getMinimumTier());
    Map<String, Object> details = new LinkedHashMap<>();
    if (ex.getFeatureId() != null) details.put("featureId", ex.getFeatureId());
    if (ex.getMinimumTier() != null) details.put("minimumTier", ex.getMinimumTier());
    return ApiError.of(
        402,
        "Payment Required",
        "FEATURE_NOT_AVAILABLE",
        ex.getMessage(),
        req.getRequestURI(),
        details);
  }

  @ExceptionHandler(QuotaExceededException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public ApiError handleQuotaExceeded(QuotaExceededException ex, HttpServletRequest req) {
    log.warn(
        "Quota exceeded: quotaType={}, limit={}, used={}",
        ex.getQuotaType(),
        ex.getLimit(),
        ex.getUsed());
    Map<String, Object> details = new LinkedHashMap<>();
    if (ex.getQuotaType() != null) details.put("quotaType", ex.getQuotaType());
    if (ex.getLimit() != null) details.put("limit", ex.getLimit());
    if (ex.getUsed() != null) details.put("used", ex.getUsed());
    return ApiError.of(
        429, "Too Many Requests", "QUOTA_EXCEEDED", ex.getMessage(), req.getRequestURI(), details);
  }

  // ---------------------------------------------------------------------------
  // Common exceptions
  // ---------------------------------------------------------------------------

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError handleNotFound(NotFoundException ex, HttpServletRequest req) {
    log.info("Resource not found: {}", ex.getMessage());
    return ApiError.of(404, "Not Found", "NOT_FOUND", ex.getMessage(), req.getRequestURI());
  }

  @ExceptionHandler(DomainException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleDomain(DomainException ex, HttpServletRequest req) {
    log.info("Domain rule violation: {}", ex.getMessage());
    return ApiError.of(
        400, "Bad Request", "DOMAIN_RULE_VIOLATION", ex.getMessage(), req.getRequestURI());
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

  @ExceptionHandler(OptimisticLockConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiError handleOptimisticLockConflict(
      OptimisticLockConflictException ex, HttpServletRequest req) {
    log.warn(
        "Application Optimistic lock conflict: entity={}, id={}, clientV={}, currentV={}",
        ex.getEntityType(),
        ex.getEntityId(),
        ex.getClientVersion(),
        ex.getCurrentVersion());
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("entityType", ex.getEntityType());
    details.put("entityId", ex.getEntityId());
    details.put("clientVersion", ex.getClientVersion());
    details.put("currentVersion", ex.getCurrentVersion());
    return ApiError.of(
        HttpStatus.CONFLICT.value(),
        "Conflict",
        "OPTIMISTIC_LOCK",
        ex.getMessage(),
        req.getRequestURI(),
        details);
  }

  @ExceptionHandler(ForbiddenOperationException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiError handleForbiddenOperation(ForbiddenOperationException ex, HttpServletRequest req) {
    log.info("Forbidden operation: {}", ex.getMessage());
    return ApiError.of(
        403, "Forbidden", "FORBIDDEN_OPERATION", ex.getMessage(), req.getRequestURI());
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
