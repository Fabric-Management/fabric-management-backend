package com.fabricmanagement.platform.organization.dto;

import com.fabricmanagement.platform.common.dto.CertificationSummary;
import com.fabricmanagement.platform.organization.domain.OrganizationCertification;
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
public class OrganizationCertificationDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID organizationId;
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

  public static OrganizationCertificationDto from(
      OrganizationCertification entity, CertificationSummary certificationSummary) {
    return OrganizationCertificationDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .organizationId(entity.getOrganization() != null ? entity.getOrganization().getId() : null)
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
