package com.fabricmanagement.platform.auth.dto;

import com.fabricmanagement.platform.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for the "Active Sessions" listing in Settings > Security.
 *
 * @param id session id (RefreshToken PK) used for per-session revoke
 * @param deviceName human-readable label, e.g. "Chrome on Windows"
 * @param ipAddress IP address at login time
 * @param createdAt when this session was created
 * @param lastActiveAt most recent activity (token rotation or creation)
 * @param isCurrent true when this session matches the requester's current token
 */
public record ActiveSessionDto(
    UUID id,
    String deviceName,
    String ipAddress,
    Instant createdAt,
    Instant lastActiveAt,
    boolean isCurrent) {

  public static ActiveSessionDto from(RefreshToken token, boolean isCurrent) {
    return new ActiveSessionDto(
        token.getId(),
        token.getDeviceName() != null ? token.getDeviceName() : "Unknown Device",
        token.getIpAddress(),
        token.getCreatedAt(),
        token.getUpdatedAt() != null ? token.getUpdatedAt() : token.getCreatedAt(),
        isCurrent);
  }
}
