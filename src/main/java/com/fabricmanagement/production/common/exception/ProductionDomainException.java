package com.fabricmanagement.production.common.exception;

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
 * └── ProductionDomainException          ← this class (400)
 *     ├── InsufficientStockException     (422)
 *     ├── InvalidStatusTransitionException (409)
 *     ├── FiberDomainException           (fiber master data)
 *     ├── RecipeDomainException          (BOM / formula)
 *     ├── FiberBatchDomainException      (batch execution)
 *     ├── YarnDomainException            (yarn production — future)
 *     └── ProcessDomainException         (dye &amp; finishing — future)
 * </pre>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Direct use for generic production rule violations
 * throw new ProductionDomainException("Fiber composition percentages must sum to 100.");
 *
 * // Module-specific subclass (preferred)
 * throw new FiberDomainException("Fiber is already inactive and cannot be updated.");
 * }</pre>
 *
 * <p>For resource-not-found cases (404), use {@link
 * com.fabricmanagement.common.infrastructure.web.exception.NotFoundException} directly or extend it
 * within the module (e.g. {@code YarnNotFoundException extends NotFoundException}).
 */
public class ProductionDomainException extends RuntimeException {

  public ProductionDomainException(String message) {
    super(message);
  }

  public ProductionDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
