package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.BatchCertificationChangeReason;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing batch certification (PUT).
 *
 * <p>Supports partial update: only non-null fields are applied. changeReason is required for audit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBatchCertificationRequest {

  @NotNull(message = "Change reason is required")
  private BatchCertificationChangeReason changeReason;

  private String certNumber;
  private LocalDate validFrom;
  private LocalDate validUntil;
  private String certifyingBodyRef;
  private String documentUrl;
  private String remarks;
}
