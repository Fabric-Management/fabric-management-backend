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
 * Request for partial acceptance split (QC kısmi kabul).
 *
 * <p>Source batch: remaining quantity gets rejectedStatus. New batch: acceptedQuantity with
 * AVAILABLE, parentBatchId = source.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartialAcceptanceSplitRequest {

  @NotNull(message = "Accepted quantity is required")
  @DecimalMin(value = "0.01", message = "Accepted quantity must be greater than 0")
  private BigDecimal acceptedQuantity;

  @NotBlank(message = "Reason is required")
  private String reason;

  /**
   * Status for the rejected remainder in the source batch. Default QC_REJECTED.
   *
   * <p>Must be one of: QC_REJECTED, RETURNED, DESTROYED.
   */
  @Builder.Default private BatchStatus rejectedStatus = BatchStatus.QC_REJECTED;
}
