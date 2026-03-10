package com.fabricmanagement.production.masterdata.fiber.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

/**
 * Base exception for fiber master data domain rule violations.
 *
 * <p>Throw this (or a subclass) for any business rule violation specific to fiber definitions,
 * compositions, or categorization. Examples:
 *
 * <ul>
 *   <li>Fiber composition percentages do not sum to 100%
 *   <li>Attempting to update a deactivated fiber
 *   <li>Duplicate fiber name within the same tenant
 *   <li>Invalid blending ratio for a specific fiber type
 * </ul>
 *
 * <p>For not-found cases, use {@link
 * com.fabricmanagement.common.infrastructure.web.exception.NotFoundException} with a message like
 * {@code "Fiber not found: " + id}.
 */
public class FiberDomainException extends ProductionDomainException {

  public FiberDomainException(String message) {
    super(message);
  }

  public FiberDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
