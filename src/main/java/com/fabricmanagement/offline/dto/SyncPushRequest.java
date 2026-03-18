package com.fabricmanagement.offline.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

/**
 * Represents the sync metadata envelope sent by the mobile client when pushing offline-created
 * entities.
 *
 * <p>This is used as part of the push sync payload alongside the entity-specific data.
 */
@Data
public class SyncPushRequest {

  @NotNull(message = "Offline ID is required")
  private UUID offlineId;

  @NotNull(message = "Device ID is required")
  @Size(max = 100, message = "Device ID must be 100 characters or less")
  @Pattern(
      regexp = "^[a-zA-Z0-9_\\-.:]+$",
      message =
          "Device ID can only contain alphanumeric characters, underscores, hyphens, dots, and colons")
  private String deviceId;

  @NotNull(message = "Offline created at timestamp is required")
  private Instant offlineCreatedAt;

  /** The entity type being synced (e.g. QUOTE, SALES_ORDER, TRADING_PARTNER, SAMPLE_REQUEST). */
  @NotNull(message = "Entity type is required")
  private String entityType;

  /** Typed payload of the entity data. Parsed by the SyncService based on entityType. */
  @NotNull(message = "Payload is required")
  private Map<String, Object> payload;
}
