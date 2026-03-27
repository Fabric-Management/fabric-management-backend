package com.fabricmanagement.production.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import lombok.Getter;

@Getter
public class OptimisticLockConflictException extends DomainException {

  private final String entityType;
  private final String entityId;
  private final Long clientVersion;
  private final Long currentVersion;

  public OptimisticLockConflictException(
      String entityType, Object entityId, Long clientVersion, Long currentVersion) {
    super(
        String.format(
            "%s was modified by another user. Your version: %d, current version: %d.",
            entityType, clientVersion, currentVersion),
        "OPTIMISTIC_LOCK",
        409);
    this.entityType = entityType;
    this.entityId = entityId != null ? entityId.toString() : "unknown";
    this.clientVersion = clientVersion;
    this.currentVersion = currentVersion;
    withDetail("entityType", entityType);
    withDetail("entityId", this.entityId);
    withDetail("clientVersion", clientVersion);
    withDetail("currentVersion", currentVersion);
  }
}
