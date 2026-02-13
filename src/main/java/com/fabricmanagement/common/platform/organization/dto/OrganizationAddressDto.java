package com.fabricmanagement.common.platform.organization.dto;

import com.fabricmanagement.common.platform.organization.domain.OrganizationAddress;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for OrganizationAddress junction entity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationAddressDto {

  private UUID organizationId;
  private UUID addressId;
  private Boolean isPrimary;
  private Boolean isHeadquarters;

  public static OrganizationAddressDto from(OrganizationAddress entity) {
    return OrganizationAddressDto.builder()
        .organizationId(entity.getOrganizationId())
        .addressId(entity.getAddressId())
        .isPrimary(entity.getIsPrimary())
        .isHeadquarters(entity.getIsHeadquarters())
        .build();
  }
}
