package com.fabricmanagement.production.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Base exception for all production domain rule violations.
 *
 * <p>All business rule violations within the production module (Fiber, Yarn, Fabric, Dye &
 * Finishing, Recipe, Planning) should extend this class. This allows the {@code
 * GlobalExceptionHandler} to distinguish production-specific errors from general domain errors and
 * return the correct {@code PRODUCTION_RULE_VIOLATION} error code.
 *
 * <h2>Exception Hierarchy</h2>
 *
 * <pre>
 * RuntimeException
 * └── DomainException                          ← common abstract base
 *     └── ProductionDomainException            ← this class (400 default)
 *         ├── InsufficientStockException       (422 — overrides httpStatus)
 *         ├── InvalidStatusTransitionException (409 — overrides httpStatus)
 *         ├── FiberDomainException             (400)
 *         ├── RecipeDomainException            (400)
 *         ├── BatchDomainException             (400)
 *         ├── WorkOrderDomainException         (400)
 *         ├── YarnDomainException              (future)
 *         └── ProcessDomainException           (future)
 * </pre>
 */
public class ProductionDomainException extends DomainException {

  /** Generic production domain rule violation — HTTP 400. */
  public ProductionDomainException(String message) {
    super(message, "PRODUCTION_RULE_VIOLATION", 400);
  }

  /** Generic production domain rule violation with cause — HTTP 400. */
  public ProductionDomainException(String message, Throwable cause) {
    super(message, "PRODUCTION_RULE_VIOLATION", 400, cause);
  }

  /**
   * Constructor for subclasses that override the default errorCode and httpStatus. Use this when
   * the subclass represents a distinct HTTP status (422, 409, etc.).
   */
  protected ProductionDomainException(String message, String errorCode, int httpStatus) {
    super(message, errorCode, httpStatus);
  }

  /** Constructor for subclasses that override the default errorCode and httpStatus, with cause. */
  protected ProductionDomainException(
      String message, String errorCode, int httpStatus, Throwable cause) {
    super(message, errorCode, httpStatus, cause);
  }
}
