package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionOverride;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PermissionOverrideDto {
  private UUID id;
  private UUID userId;
  private String resource;
  private String action;
  private DataScope dataScope;
  private String reason;
  private Instant expiresAt;
  private boolean isExpired;

  public static PermissionOverrideDto from(PermissionOverride override) {
    if (override == null) return null;
    return PermissionOverrideDto.builder()
        .id(override.getId())
        .userId(override.getUserId())
        .resource(override.getResource())
        .action(override.getAction())
        .dataScope(override.getDataScope())
        .reason(override.getReason())
        .expiresAt(override.getExpiresAt())
        .isExpired(override.isExpired())
        .build();
  }
}
