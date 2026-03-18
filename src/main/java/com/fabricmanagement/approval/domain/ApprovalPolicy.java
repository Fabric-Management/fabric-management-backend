package com.fabricmanagement.approval.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tenant bazlı Onay Politikası kural tanımı. Hangi tür işlem, hangi güven düzeyindeki birinden
 * çıkarsa kimin onayına düşecek?
 */
@Entity
@Table(schema = "common_approval", name = "approval_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalPolicy extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 50)
  private ApprovalEntityType entityType;

  // ALL, PROBATION, STANDARD
  @Enumerated(EnumType.STRING)
  @Column(name = "required_for_level", nullable = false, length = 50)
  private PolicyTargetLevel requiredForLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "approver_role", nullable = false, length = 50)
  private ApproverRole approverRole;

  @Column(name = "promotion_threshold", nullable = false)
  private int promotionThreshold = 10;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public ApprovalPolicy(
      UUID tenantId,
      ApprovalEntityType entityType,
      PolicyTargetLevel requiredForLevel,
      ApproverRole approverRole,
      int promotionThreshold) {
    if (entityType == null) {
      throw new IllegalArgumentException("entityType cannot be null");
    }
    if (approverRole == null) {
      throw new IllegalArgumentException("approverRole cannot be null");
    }
    if (promotionThreshold < 1) {
      throw new IllegalArgumentException("promotionThreshold must be >= 1");
    }
    this.setTenantId(tenantId);
    this.entityType = entityType;
    this.requiredForLevel = requiredForLevel;
    this.approverRole = approverRole;
    this.promotionThreshold = promotionThreshold;
  }

  @Override
  protected String getModuleCode() {
    return "APOL"; // Approval POLicy
  }

  public void update(
      PolicyTargetLevel requiredForLevel, ApproverRole approverRole, int promotionThreshold) {
    this.requiredForLevel = requiredForLevel;
    this.approverRole = approverRole;
    this.promotionThreshold = promotionThreshold;
  }

  public void toggleActive(boolean active) {
    this.isActive = active;
  }
}
