package com.fabricmanagement.platform.admin.dto;

import com.fabricmanagement.platform.user.dto.UserDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** One user as the platform staff screen lists a tenant's users. */
@Schema(description = "A tenant's user as the platform staff screen lists it")
public record PlatformTenantUser(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String uid,
    String firstName,
    String lastName,
    String displayName,
    @Schema(description = "The e-mail the user signs in with; absent when the user has no login")
        String email,
    @Schema(description = "Role name for display") String role,
    @Schema(description = "Primary department name for display") String departmentName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {

  public static PlatformTenantUser of(UserDto user, String email) {
    return new PlatformTenantUser(
        user.getId(),
        user.getUid(),
        user.getFirstName(),
        user.getLastName(),
        user.getDisplayName(),
        email,
        user.getRole(),
        user.getDepartmentName(),
        !Boolean.FALSE.equals(user.getIsActive()));
  }
}
