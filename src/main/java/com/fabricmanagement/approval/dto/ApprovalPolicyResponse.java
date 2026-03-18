package com.fabricmanagement.approval.dto;

import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalPolicy;
import com.fabricmanagement.approval.domain.ApproverRole;
import com.fabricmanagement.approval.domain.PolicyTargetLevel;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * ApprovalPolicy entity'sinin API response DTO'su. BaseEntity alanlarını (deletedAt, version,
 * createdBy vb.) gizleyerek istemciye yalnızca iş alanlarını sunar.
 */
@Data
@Builder
public class ApprovalPolicyResponse {
  private UUID id;
  private String uid;
  private ApprovalEntityType entityType;
  private PolicyTargetLevel requiredForLevel;
  private ApproverRole approverRole;
  private int promotionThreshold;
  private boolean active;
  private Instant createdAt;
  private Instant updatedAt;

  public static ApprovalPolicyResponse from(ApprovalPolicy entity) {
    return ApprovalPolicyResponse.builder()
        .id(entity.getId())
        .uid(entity.getUid())
        .entityType(entity.getEntityType())
        .requiredForLevel(entity.getRequiredForLevel())
        .approverRole(entity.getApproverRole())
        .promotionThreshold(entity.getPromotionThreshold())
        .active(entity.isActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
