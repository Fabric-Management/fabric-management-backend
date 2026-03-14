package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.BatchCertificationScope;
import jakarta.validation.constraints.NotNull;
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
public class AddBatchCertificationRequest {

  @NotNull(message = "Certification ID is required")
  private UUID certificationId;

  private BatchCertificationScope scope;
  private UUID partnerCertificationId;
  private UUID orgCertificationId;
  private String certNumber;
  private LocalDate validFrom;
  private LocalDate validUntil;
  private String certifyingBodyRef;
  private String documentUrl;
  private String remarks;

  /** Set true when form was populated from autoFill (partner/facility cert). GOTS audit. */
  private Boolean isAutoFilled;
}
