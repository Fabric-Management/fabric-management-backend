package com.fabricmanagement.procurement.subcontract.dto;

import jakarta.validation.constraints.DecimalMin;
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
public class UpdateSubcontractOrderRequest {

  private UUID outputProductId;

  @DecimalMin(value = "0.001", message = "Expected output quantity must be greater than zero")
  private BigDecimal expectedOutputQty;

  private BigDecimal agreedUnitPrice;
  private String currency;
  private LocalDate expectedReturnDate;
  private String notes;
}
