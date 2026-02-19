package com.fabricmanagement.common.platform.organization.dto;

import com.fabricmanagement.common.platform.organization.domain.OrganizationContact;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for OrganizationContact junction entity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationContactDto {

  private UUID organizationId;
  private UUID contactId;
  private Boolean isDefault;
  private String department;

  public static OrganizationContactDto from(OrganizationContact entity) {
    return OrganizationContactDto.builder()
        .organizationId(entity.getOrganizationId())
        .contactId(entity.getContactId())
        .isDefault(entity.getIsDefault())
        .department(entity.getDepartment())
        .build();
  }
}
