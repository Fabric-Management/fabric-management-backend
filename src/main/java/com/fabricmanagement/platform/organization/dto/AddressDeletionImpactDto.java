package com.fabricmanagement.platform.organization.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO that describes the impact of deleting an organization address.
 *
 * <p>Used by the frontend to show a confirmation dialog with the number of affected users before
 * proceeding with the deletion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDeletionImpactDto {

  private UUID addressId;
  private String addressLabel;
  private int affectedUserCount;
  private List<AffectedUserDto> affectedUsers;
  private boolean hasOrganizationAssignment;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AffectedUserDto {
    private UUID userId;
    private String displayName;
    private boolean isPrimaryLocation;
  }
}
