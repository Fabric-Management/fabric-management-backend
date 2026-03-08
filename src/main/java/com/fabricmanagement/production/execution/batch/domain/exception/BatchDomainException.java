package com.fabricmanagement.production.execution.batch.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

/**
 * Base exception for batch execution domain rule violations.
 *
 * <p>Throw this (or a subclass) for any business rule violation during batch lifecycle management.
 * Prefer the more specific exceptions where applicable:
 *
 * <ul>
 *   <li>{@link com.fabricmanagement.production.common.exception.InsufficientStockException} — when
 *       a reserve or consume operation exceeds available quantity
 *   <li>{@link com.fabricmanagement.production.common.exception.InvalidStatusTransitionException} —
 *       when a batch is in a state that does not allow the requested operation
 * </ul>
 *
 * <p>Use this class directly for batch-specific violations not covered by the above, for example:
 *
 * <ul>
 *   <li>A batch references a fiber that has since been deactivated
 *   <li>Batch lot number already exists for this tenant
 * </ul>
 */
public class BatchDomainException extends ProductionDomainException {

  public BatchDomainException(String message) {
    super(message);
  }

  public BatchDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
