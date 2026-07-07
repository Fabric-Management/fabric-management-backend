package com.fabricmanagement.sales.quote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class AddQuoteLineRequest {

  @NotNull(message = "Product ID is required")
  private UUID productId;

  private UUID qualityGradeId;

  private UUID colorId;

  @Valid private List<QuoteLineLotSelectionRequest> selectedLots;

  @NotNull(message = "Requested quantity is required")
  @DecimalMin(value = "0.001", message = "Requested quantity must be greater than zero")
  private BigDecimal requestedQty;

  @NotBlank(message = "Unit is required")
  @Size(max = 20, message = "Unit must be 20 characters or less")
  private String unit;

  @NotNull(message = "Offered price is required")
  @DecimalMin(value = "0.0001", message = "Offered price must be greater than zero")
  private BigDecimal offeredPrice;
}
