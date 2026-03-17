package com.fabricmanagement.production.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Base exception for all production domain rule violations.
 *
 * <p>All business rule violations within the production module (Fiber, Yarn, Fabric, Dye &amp;
 * Finishing, Recipe, Planning) should extend this class. This allows the {@code
 * GlobalExceptionHandler} to distinguish production-specific errors from general domain errors and
 * return the correct {@code PRODUCTION_RULE_VIOLATION} error code.
 *
 * <h2>Exception Hierarchy</h2>
 *
 * <pre>
 * RuntimeException
 * └── DomainException                      ← common abstract base
 *     └── ProductionDomainException        ← this class (400)
 *         ├── InsufficientStockException   (422)
 *         ├── InvalidStatusTransitionException (409)
 *         ├── FiberDomainException
 *         ├── RecipeDomainException
 *         ├── BatchDomainException
 *         ├── YarnDomainException          (future)
 *         └── ProcessDomainException       (future)
 * </pre>
 */
public class ProductionDomainException extends DomainException {

  public ProductionDomainException(String message) {
    super(message, "PRODUCTION_RULE_VIOLATION", 400);
  }

  public ProductionDomainException(String message, Throwable cause) {
    super(message, "PRODUCTION_RULE_VIOLATION", 400, cause);
  }
}
