package com.fabricmanagement.approval.dto;

import com.fabricmanagement.approval.domain.PromotionRequestStatus;
import com.fabricmanagement.approval.domain.PromotionTriggerType;
import com.fabricmanagement.approval.domain.UserPromotionRequest;
import com.fabricmanagement.common.infrastructure.user.UserTrustLevel;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserPromotionResponse {
  private UUID id;
  private String uid;
  private UUID userId;
  private UserTrustLevel fromLevel;
  private UserTrustLevel toLevel;
  private PromotionRequestStatus status;
  private PromotionTriggerType triggeredBy;
  private UUID reviewedBy;
  private String adminNote;
  private int rejectionCount;
  private int approvedTransactionCount;
  private OffsetDateTime createdAt;

  public static UserPromotionResponse from(UserPromotionRequest entity) {
    return UserPromotionResponse.builder()
        .id(entity.getId())
        .uid(entity.getUid())
        .userId(entity.getUserId())
        .fromLevel(entity.getFromLevel())
        .toLevel(entity.getToLevel())
        .status(entity.getStatus())
        .triggeredBy(entity.getTriggeredBy())
        .reviewedBy(entity.getReviewedBy())
        .adminNote(entity.getAdminNote())
        .rejectionCount(entity.getRejectionCount())
        .approvedTransactionCount(entity.getApprovedTransactionCount())
        .createdAt(
            entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
        .build();
  }
}
