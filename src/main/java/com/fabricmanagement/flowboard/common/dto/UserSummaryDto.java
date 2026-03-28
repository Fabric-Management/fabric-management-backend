package com.fabricmanagement.flowboard.common.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {
  private UUID userId;
  private String fullName;
  private String avatarInitials;

  public static UserSummaryDto of(
      UUID userId, String firstName, String lastName, String displayName) {
    String name = displayName != null ? displayName : (firstName + " " + lastName);
    String initials = "U";
    if (firstName != null && !firstName.isBlank()) {
      initials = String.valueOf(firstName.charAt(0)).toUpperCase();
    } else if (displayName != null && !displayName.isBlank()) {
      initials = String.valueOf(displayName.charAt(0)).toUpperCase();
    }

    return UserSummaryDto.builder().userId(userId).fullName(name).avatarInitials(initials).build();
  }
}
