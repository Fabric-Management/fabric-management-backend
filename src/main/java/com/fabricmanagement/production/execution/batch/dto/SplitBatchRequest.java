package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for POST /batches/{id}/split.
 *
 * <p>Splits a batch: acceptedQuantity → new AVAILABLE batch; remainder stays in source with
 * RETURNED or DESTROYED status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitBatchRequest {

  @NotNull(message = "Accepted quantity is required")
  @DecimalMin(value = "0.01", message = "Accepted quantity must be greater than 0")
  private BigDecimal acceptedQuantity;

  @NotBlank(message = "Reason is required")
  private String reason;

  /**
   * Status for the remainder in the source batch. Default RETURNED.
   *
   * <p>Must be one of: RETURNED, DESTROYED.
   */
  @Builder.Default private BatchStatus rejectedStatus = BatchStatus.RETURNED;
}
