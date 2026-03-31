package com.fabricmanagement.platform.tradingpartner.dto;

import com.fabricmanagement.platform.common.dto.CertificationSummary;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerCertification;
import java.time.Instant;
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
public class TradingPartnerCertificationDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID tradingPartnerId;
  private UUID certificationId;
  private CertificationSummary certification;
  private String licenseNo;
  private LocalDate issuedAt;
  private LocalDate validUntil;
  private String documentRef;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static TradingPartnerCertificationDto from(
      TradingPartnerCertification entity, CertificationSummary certificationSummary) {
    return TradingPartnerCertificationDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .tradingPartnerId(
            entity.getTradingPartner() != null ? entity.getTradingPartner().getId() : null)
        .certificationId(entity.getCertificationId())
        .certification(certificationSummary)
        .licenseNo(entity.getLicenseNo())
        .issuedAt(entity.getIssuedAt())
        .validUntil(entity.getValidUntil())
        .documentRef(entity.getDocumentRef())
        .isActive(entity.getIsActive())
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
