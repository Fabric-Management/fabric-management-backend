package com.fabricmanagement.production.core.workorder.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

/**
 * The tenant has not configured costing for a work order's module yet (no active price list or no
 * default cost template). An expected state for a tenant that has not set costing up, not a fault:
 * the planned cost simply stays empty until the configuration exists and it is recalculated.
 *
 * <p>422, like the costing error it replaces on the manual recalculation endpoint: the request is
 * valid, the tenant's configuration cannot satisfy it yet.
 */
public class PlannedCostConfigurationMissingException extends ProductionDomainException {

  public PlannedCostConfigurationMissingException(String message) {
    super(message, "PLANNED_COST_CONFIGURATION_MISSING", 422);
  }
}
