package com.fabricmanagement.production.execution.batch.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One parent batch contribution to a blended (child) batch. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlendParentRequest {

  @NotNull(message = "Parent batch ID is required")
  private UUID parentBatchId;

  @NotNull(message = "Consumed quantity is required")
  @Positive(message = "Consumed quantity must be positive")
  private BigDecimal consumedQuantity;

  @NotNull(message = "Consumption percentage is required")
  @DecimalMin(value = "0.01", message = "Consumption percentage must be between 0.01 and 100")
  @DecimalMax(value = "100", message = "Consumption percentage must be between 0.01 and 100")
  private BigDecimal consumptionPercentage;
}
