package com.fabricmanagement.common.platform.organization.dto;

import com.fabricmanagement.common.platform.organization.domain.OrganizationCertification;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCertificationDto;
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
  private FiberCertificationDto certification;
  private String licenseNo;
  private LocalDate issuedAt;
  private LocalDate validUntil;
  private String documentRef;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static OrganizationCertificationDto from(OrganizationCertification entity) {
    return OrganizationCertificationDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .organizationId(entity.getOrganization() != null ? entity.getOrganization().getId() : null)
        .certificationId(
            entity.getCertification() != null ? entity.getCertification().getId() : null)
        .certification(
            entity.getCertification() != null
                ? FiberCertificationDto.from(entity.getCertification())
                : null)
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
