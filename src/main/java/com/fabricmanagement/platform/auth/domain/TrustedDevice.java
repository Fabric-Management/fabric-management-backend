package com.fabricmanagement.platform.auth.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "common_trusted_device", schema = "common_auth")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrustedDevice extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "device_hash", nullable = false, unique = true)
  private String deviceHash;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 1000)
  private String userAgent;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Builder
  public TrustedDevice(
      UUID userId, String deviceHash, String ipAddress, String userAgent, Instant expiresAt) {
    this.userId = userId;
    this.deviceHash = deviceHash;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.expiresAt = expiresAt;
    this.lastUsedAt = Instant.now();
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  @Override
  protected String getModuleCode() {
    return "TRU";
  }
}
