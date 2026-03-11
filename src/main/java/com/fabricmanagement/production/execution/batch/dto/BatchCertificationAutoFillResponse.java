package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.BatchCertificationScope;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Suggested values for batch certification form, auto-filled from TradingPartnerCertification
 * (SUPPLIER scope) or OrganizationCertification (FACILITY scope).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCertificationAutoFillResponse {

  private UUID certificationId;
  private String certificationCode;
  private String certificationName;
  private BatchCertificationScope scope;
  private UUID partnerCertificationId;
  private UUID orgCertificationId;
  private String certNumber;
  private LocalDate validFrom;
  private LocalDate validUntil;
  private String certifyingBodyRef;
  private String documentUrl;
  private String remarks;
}
