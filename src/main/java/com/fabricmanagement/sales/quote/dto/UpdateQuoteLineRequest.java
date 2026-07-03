package com.fabricmanagement.sales.quote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class UpdateQuoteLineRequest {

  @NotNull(message = "Requested quantity is required")
  @DecimalMin(value = "0.001", message = "Requested quantity must be greater than zero")
  private BigDecimal requestedQty;

  @NotBlank(message = "Unit is required")
  private String unit;

  @NotNull(message = "Offered price is required")
  @DecimalMin(value = "0.0001", message = "Offered price must be greater than zero")
  private BigDecimal offeredPrice;
}
