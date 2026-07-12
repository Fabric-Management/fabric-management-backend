package com.fabricmanagement.common.infrastructure.events;

import java.time.Instant;
import java.util.UUID;

public record FollowUpFlagDto(
    UUID id,
    String entityType,
    UUID entityId,
    String entityRef,
    String summary,
    String referenceType,
    UUID referenceId,
    FollowUpFlagStatus status,
    Instant createdAt) {

  static FollowUpFlagDto from(IncompleteFollowUpFlag flag) {
    return new FollowUpFlagDto(
        flag.getId(),
        flag.getEntityType(),
        flag.getEntityId(),
        flag.getEntityRef(),
        flag.getSummary(),
        flag.getReferenceType(),
        flag.getReferenceId(),
        flag.getStatus(),
        flag.getCreatedAt());
  }
}
