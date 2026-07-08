package com.fabricmanagement.sales.quote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuoteLineLotSelectionRequest(
    @NotNull UUID lotId,
    List<UUID> stockUnitIds,
    @DecimalMin(value = "0.001", message = "Lot quantity must be greater than zero")
        BigDecimal quantity) {

  public QuoteLineLotSelectionRequest(UUID lotId, List<UUID> stockUnitIds) {
    this(lotId, stockUnitIds, null);
  }
}
