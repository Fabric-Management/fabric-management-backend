package com.fabricmanagement.procurement.subcontract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubcontractOrderRequest {

  @NotNull(message = "Work order ID is required")
  private UUID workOrderId;

  @NotNull(message = "Trading partner (subcontractor) ID is required")
  private UUID tradingPartnerId;

  @NotNull(message = "Input product is required for subcontract orders")
  private UUID inputProductId;

  private UUID outputProductId;

  @DecimalMin(value = "0.001", message = "Product sent quantity must be greater than zero")
  private BigDecimal productSentQty;

  @DecimalMin(value = "0.001", message = "Expected output quantity must be greater than zero")
  private BigDecimal expectedOutputQty;

  private BigDecimal agreedUnitPrice;
  private String currency;
  private LocalDate expectedReturnDate;
  private String notes;
}
