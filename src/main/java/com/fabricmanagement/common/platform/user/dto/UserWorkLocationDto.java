package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.communication.dto.AddressDto;
import com.fabricmanagement.common.platform.user.domain.UserWorkLocation;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWorkLocationDto {
  private UUID userId;
  private UUID orgAddressId;
  private UUID organizationId;
  private Boolean isPrimary;
  private String notes;
  private AddressDto address;
  private String organizationName;

  public static UserWorkLocationDto from(UserWorkLocation wl) {
    if (wl == null) return null;

    var builder =
        UserWorkLocationDto.builder()
            .userId(wl.getUserId())
            .orgAddressId(wl.getOrgAddressId())
            .isPrimary(wl.getIsPrimary())
            .notes(wl.getNotes());

    if (wl.getOrganizationAddress() != null) {
      builder.organizationId(wl.getOrganizationAddress().getOrganizationId());

      if (wl.getOrganizationAddress().getAddress() != null) {
        builder.address(AddressDto.from(wl.getOrganizationAddress().getAddress()));
      }
      if (wl.getOrganizationAddress().getOrganization() != null) {
        builder.organizationName(wl.getOrganizationAddress().getOrganization().getName());
      }
    }

    return builder.build();
  }
}
