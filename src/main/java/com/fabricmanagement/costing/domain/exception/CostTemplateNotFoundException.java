package com.fabricmanagement.costing.domain.exception;

import java.util.UUID;

/** Thrown when a tenant has no default cost template for the requested module. */
public class CostTemplateNotFoundException extends CostingDomainException {

  public CostTemplateNotFoundException(UUID tenantId, String moduleType) {
    super(
        "No default cost template configured for module: "
            + moduleType
            + " (tenantId="
            + tenantId
            + ")");
  }
}
