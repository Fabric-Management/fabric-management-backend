package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a specific access override applied directly to a user for modifying default behavior.
 */
@Entity
@Table(name = "permission_override")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionOverride extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "resource", nullable = false, length = 50)
  private String resource;

  @Column(name = "action", nullable = false, length = 20)
  private String action;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_scope", length = 20)
  private DataScope dataScope;

  @Column(name = "reason", columnDefinition = "TEXT")
  private String reason;

  @Column(name = "granted_by", nullable = false)
  private UUID grantedBy;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Override
  protected String getModuleCode() {
    return "PRMO";
  }

  public boolean isExpired() {
    return expiresAt != null && expiresAt.isBefore(Instant.now());
  }
}
