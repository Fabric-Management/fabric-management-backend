package com.fabricmanagement.platform.organization.dto;

import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for Organization entity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private String name;
  private String legalName;
  private String taxId;
  private OrganizationType organizationType;
  private UUID parentOrganizationId;
  private String preferredCurrency;
  private Instant createdAt;
  private Boolean isActive;

  /**
   * Create DTO from entity.
   *
   * @param organization Organization entity
   * @return DTO
   */
  public static OrganizationDto from(Organization organization) {
    if (organization == null) {
      return null;
    }
    return OrganizationDto.builder()
        .id(organization.getId())
        .tenantId(organization.getTenantId())
        .uid(organization.getUid())
        .name(organization.getName())
        .legalName(organization.getLegalName())
        .taxId(organization.getTaxId())
        .organizationType(organization.getOrganizationType())
        .parentOrganizationId(organization.getParentOrganizationId())
        .preferredCurrency(organization.getPreferredCurrency())
        .createdAt(organization.getCreatedAt())
        .isActive(organization.getIsActive())
        .build();
  }
}
