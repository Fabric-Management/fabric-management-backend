package com.fabricmanagement.approval.dto;

import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalRequest;
import com.fabricmanagement.approval.domain.ApprovalRequestStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** ApprovalRequest entity'sinin API response DTO'su. BaseEntity internal alanlarını gizler. */
@Data
@Builder
public class ApprovalRequestResponse {
  private UUID id;
  private String uid;
  private ApprovalEntityType entityType;
  private UUID entityId;
  private UUID policyId;
  private UUID requestedBy;
  private UUID approverId;
  private ApprovalRequestStatus status;
  private OffsetDateTime approvedAt;
  private String rejectionReason;
  private OffsetDateTime expiresAt;
  private OffsetDateTime createdAt;

  public static ApprovalRequestResponse from(ApprovalRequest entity) {
    return ApprovalRequestResponse.builder()
        .id(entity.getId())
        .uid(entity.getUid())
        .entityType(entity.getEntityType())
        .entityId(entity.getEntityId())
        .policyId(entity.getPolicyId())
        .requestedBy(entity.getRequestedBy())
        .approverId(entity.getApproverId())
        .status(entity.getStatus())
        .approvedAt(entity.getApprovedAt())
        .rejectionReason(entity.getRejectionReason())
        .expiresAt(entity.getExpiresAt())
        .createdAt(
            entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
        .build();
  }
}
