package com.fabricmanagement.production.execution.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for adjusting a batch's total quantity (physical count correction, write-off, etc.).
 *
 * <p>Positive delta = found more stock; negative delta = write-off / loss.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentRequest {

  private Long version;

  @NotNull(message = "Delta is required")
  private BigDecimal delta;

  @NotBlank(message = "Reason is required")
  private String reason;

  private String remarks;
}
