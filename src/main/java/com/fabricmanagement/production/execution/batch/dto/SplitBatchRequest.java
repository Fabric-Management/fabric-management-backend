package com.fabricmanagement.production.execution.batch.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class SplitBatchRequest {

  @NotNull(message = "Split quantity is required")
  @DecimalMin(value = "0.01", message = "Split quantity must be greater than 0")
  private BigDecimal splitQuantity;

  @NotBlank(message = "New batch code is required")
  private String newBatchCode;

  private UUID newLocationId;

  private String remarks;
}
