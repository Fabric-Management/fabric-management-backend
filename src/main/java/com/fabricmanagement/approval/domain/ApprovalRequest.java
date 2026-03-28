package com.fabricmanagement.approval.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Onay talebini temsil eden entity sınıfı. (Polimorfik yapı: WORK_ORDER, vb.). */
@Entity
@Table(schema = "common_approval", name = "approval_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 50)
  private ApprovalEntityType entityType;

  // Hedef objenin ID'si (Örn: WorkOrderId)
  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  // Hangi kurala istinaden bu istek oluştu?
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "policy_id", nullable = false)
  private ApprovalPolicy policy;

  // İsteği kim oluşturdu?
  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  // İsteği kim onaylayacak/onayladı?
  @Column(name = "approver_id")
  private UUID approverId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ApprovalRequestStatus status = ApprovalRequestStatus.PENDING;

  @Column(name = "approved_at")
  private OffsetDateTime approvedAt;

  @Column(name = "rejection_reason", columnDefinition = "TEXT")
  private String rejectionReason;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  public ApprovalRequest(
      UUID tenantId,
      ApprovalEntityType entityType,
      UUID entityId,
      ApprovalPolicy policy,
      UUID requestedBy,
      OffsetDateTime expiresAt) {
    Objects.requireNonNull(tenantId, "tenantId cannot be null");
    Objects.requireNonNull(entityType, "entityType cannot be null");
    Objects.requireNonNull(entityId, "entityId cannot be null");
    Objects.requireNonNull(policy, "policy cannot be null");
    Objects.requireNonNull(requestedBy, "requestedBy cannot be null");
    this.setTenantId(tenantId);
    this.entityType = entityType;
    this.entityId = entityId;
    this.policy = policy;
    this.requestedBy = requestedBy;
    this.expiresAt = expiresAt;
  }

  /** Geriye uyumluluk: Var olan kodun policyId üzerinden erişmesini sağlar. */
  public UUID getPolicyId() {
    return policy != null ? policy.getId() : null;
  }

  @Override
  protected String getModuleCode() {
    return "AREQ"; // Approval REQuest
  }

  public void approve(UUID approverId, OffsetDateTime now) {
    if (this.status != ApprovalRequestStatus.PENDING) {
      throw new IllegalStateException("Only PENDING requests can be approved.");
    }
    this.status = ApprovalRequestStatus.APPROVED;
    this.approverId = approverId;
    this.approvedAt = now;
  }

  public void reject(UUID approverId, String reason) {
    if (this.status != ApprovalRequestStatus.PENDING) {
      throw new IllegalStateException("Only PENDING requests can be rejected.");
    }
    this.status = ApprovalRequestStatus.REJECTED;
    this.approverId = approverId;
    this.rejectionReason = reason;
  }

  public void cancel() {
    if (this.status != ApprovalRequestStatus.PENDING) {
      throw new IllegalStateException("Only PENDING requests can be cancelled.");
    }
    this.status = ApprovalRequestStatus.CANCELLED;
  }
}
