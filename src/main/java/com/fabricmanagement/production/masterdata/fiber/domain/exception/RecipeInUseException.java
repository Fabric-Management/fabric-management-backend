package com.fabricmanagement.production.masterdata.fiber.domain.exception;

import java.util.UUID;

/**
 * Exception thrown when a fiber's composition (recipe) cannot be modified because it has batches
 * currently committed to or active on the production floor.
 *
 * <p>Changing a recipe mid-production would break the physical and traceability consistency of
 * existing RESERVED or IN_PROGRESS lots. The operator must complete or cancel those batches before
 * the recipe can be altered.
 *
 * <h2>HTTP Response — 409 Conflict</h2>
 *
 * <pre>
 * {
 *   "code": "RECIPE_IN_USE",
 *   "message": "Fiber 'COT60_LIN40' composition cannot be changed: ...",
 *   "details": {
 *     "fiberId": "3fa85f64-...",
 *     "fiberName": "COT60_LIN40",
 *     "blockedBy": "RESERVED, IN_PROGRESS"
 *   }
 * }
 * </pre>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * if (batchRepository.existsByTenantIdAndFiberIdAndStatusIn(
 *         tenantId, fiberId, BatchStatus.PRODUCTION_ACTIVE)) {
 *   throw new RecipeInUseException(fiberId, fiber.getFiberName());
 * }
 * }</pre>
 */
public class RecipeInUseException extends FiberDomainException {

  private final UUID fiberId;
  private final String fiberName;

  public RecipeInUseException(UUID fiberId, String fiberName) {
    super(
        "Fiber '"
            + fiberName
            + "' composition cannot be changed: it has batches currently RESERVED or IN_PROGRESS"
            + " on the production floor. Complete or cancel those batches first.",
        "RECIPE_IN_USE",
        409);
    this.fiberId = fiberId;
    this.fiberName = fiberName;
    withDetail("fiberId", fiberId);
    withDetail("fiberName", fiberName);
    withDetail("blockedBy", "RESERVED, IN_PROGRESS");
  }

  public UUID getFiberId() {
    return fiberId;
  }

  public String getFiberName() {
    return fiberName;
  }
}
