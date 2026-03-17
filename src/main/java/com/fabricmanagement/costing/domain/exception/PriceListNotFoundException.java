package com.fabricmanagement.costing.domain.exception;

import java.util.UUID;

/**
 * Thrown when no active {@link com.fabricmanagement.costing.domain.price.PriceList} matches the
 * requested module type and date combination.
 */
public class PriceListNotFoundException extends CostingDomainException {

  public PriceListNotFoundException(String moduleType) {
    super("No active price list found for module: " + moduleType);
  }

  public PriceListNotFoundException(String moduleType, java.time.LocalDate onDate) {
    super("No active price list found for module '%s' on date %s".formatted(moduleType, onDate));
  }

  public PriceListNotFoundException(UUID priceListId) {
    super("Price list not found: " + priceListId);
  }
}
