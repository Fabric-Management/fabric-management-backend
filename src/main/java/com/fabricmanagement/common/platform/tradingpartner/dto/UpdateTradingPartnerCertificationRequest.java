package com.fabricmanagement.common.platform.tradingpartner.dto;

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
public class UpdateTradingPartnerCertificationRequest {

  @NotNull(message = "Version is required for optimistic locking")
  private Long version;

  private UUID certificationId;
  private String licenseNo;
  private LocalDate issuedAt;
  private LocalDate validUntil;
  private String documentRef;
}
