package com.fabricmanagement.production.execution.lineage.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBatchLineageRequest {

  private Long version;

  @NotNull(message = "Parent batch ID is required")
  private UUID parentBatchId;

  @NotNull(message = "Child batch ID is required")
  private UUID childBatchId;

  @NotNull(message = "Consumed quantity is required")
  @DecimalMin(value = "0.001", message = "Consumed quantity must be at least 0.001")
  private BigDecimal consumedQuantity;

  @NotBlank(message = "Unit is required")
  private String unit;

  @DecimalMin(value = "0.01", message = "Consumption percentage must be at least 0.01")
  private BigDecimal consumptionPercentage;

  private Instant consumedAt;

  private String processReference;

  private String remarks;
}
