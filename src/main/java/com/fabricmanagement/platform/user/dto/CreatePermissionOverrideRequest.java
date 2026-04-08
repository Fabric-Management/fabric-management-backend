package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.user.domain.DataScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class CreatePermissionOverrideRequest {

  @NotNull private UUID userId;

  @NotBlank private String resource;

  @NotBlank private String action;

  // Can be null to indicate revocation (blocking access explicitly)
  private DataScope dataScope;

  private String reason;

  private Instant expiresAt;
}
