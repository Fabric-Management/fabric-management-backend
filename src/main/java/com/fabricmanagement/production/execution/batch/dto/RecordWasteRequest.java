package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.WasteCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request for recording production waste (fire/telef) against a batch. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordWasteRequest {

  @NotNull(message = "Quantity is required")
  @DecimalMin(value = "0.001", message = "Quantity must be at least 0.001")
  private BigDecimal quantity;

  @NotNull(message = "Waste category is required")
  private WasteCategory wasteCategory;

  /** Free-text reason (e.g. machine code, defect type). Optional. */
  private String reason;

  /** Additional remarks. Optional. */
  private String remarks;
}
