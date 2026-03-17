package com.fabricmanagement.procurement.quote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

/** Fix #2 — SupplierQuoteLine entity yerine bu DTO alınmalı. */
@Data
public class AddQuoteLineRequest {

  @NotNull(message = "RFQ line ID is required")
  private UUID rfqLineId;

  @NotNull(message = "Unit price is required")
  @DecimalMin(value = "0.0001", message = "Unit price must be positive")
  private BigDecimal unitPrice;

  @NotBlank(message = "Currency is required")
  private String currency;

  @NotNull(message = "Quantity is required")
  @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
  private BigDecimal qty;

  @NotBlank(message = "Unit is required")
  private String unit;

  /** Toplu alım indirimleri — opsiyonel JSONB. */
  private String volumeDiscounts;

  private String notes;
}
