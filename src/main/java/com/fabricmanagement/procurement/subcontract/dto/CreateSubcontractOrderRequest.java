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

  private UUID materialId;

  @DecimalMin(value = "0.001", message = "Material sent quantity must be greater than zero")
  private BigDecimal materialSentQty;

  @NotNull(message = "Unit is required")
  private String unit;

  private BigDecimal agreedUnitPrice;
  private String currency;
  private LocalDate expectedReturnDate;
  private String notes;
}
