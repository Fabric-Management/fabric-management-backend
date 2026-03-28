package com.fabricmanagement.approval.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Kullanıcı seviye/yetki yükseltilme talebini tutan entity. */
@Entity
@Table(schema = "common_approval", name = "user_promotion_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPromotionRequest extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_level", nullable = false, length = 50)
  private UserTrustLevel fromLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_level", nullable = false, length = 50)
  private UserTrustLevel toLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PromotionRequestStatus status = PromotionRequestStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "triggered_by", nullable = false, length = 50)
  private PromotionTriggerType triggeredBy;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "admin_note", columnDefinition = "TEXT")
  private String adminNote;

  @Column(name = "rejection_count", nullable = false)
  private int rejectionCount = 0;

  @Column(name = "approved_transaction_count", nullable = false)
  private int approvedTransactionCount;

  public UserPromotionRequest(
      UUID tenantId,
      UUID userId,
      UserTrustLevel fromLevel,
      UserTrustLevel toLevel,
      PromotionTriggerType triggeredBy,
      int rejectionCount,
      int approvedTransactionCount) {
    Objects.requireNonNull(tenantId, "tenantId cannot be null");
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(fromLevel, "fromLevel cannot be null");
    Objects.requireNonNull(toLevel, "toLevel cannot be null");
    Objects.requireNonNull(triggeredBy, "triggeredBy cannot be null");
    this.setTenantId(tenantId);
    this.userId = userId;
    this.fromLevel = fromLevel;
    this.toLevel = toLevel;
    this.triggeredBy = triggeredBy;
    this.rejectionCount = rejectionCount;
    this.approvedTransactionCount = approvedTransactionCount;
  }

  @Override
  protected String getModuleCode() {
    return "UPRM"; // User PRoMotion
  }

  public void approve(UUID approvedById) {
    if (this.status != PromotionRequestStatus.PENDING) {
      throw new IllegalStateException("Only PENDING requests can be approved.");
    }
    this.status = PromotionRequestStatus.APPROVED;
    this.reviewedBy = approvedById;
  }

  public void reject(UUID adminId, String note) {
    if (this.status != PromotionRequestStatus.PENDING) {
      throw new IllegalStateException("Only PENDING requests can be rejected.");
    }
    this.status = PromotionRequestStatus.REJECTED;
    this.adminNote = note;
    this.reviewedBy = adminId;
    this.rejectionCount++;
  }
}
