package com.fabricmanagement.logistics.shipment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddBatchToLineRequest {

  @NotNull(message = "Batch ID is required")
  private UUID batchId;

  @NotNull(message = "Quantity is required")
  @Positive(message = "Quantity must be positive")
  private BigDecimal quantity;
}
