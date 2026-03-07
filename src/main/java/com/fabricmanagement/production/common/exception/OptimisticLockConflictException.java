package com.fabricmanagement.production.common.exception;

import lombok.Getter;

@Getter
public class OptimisticLockConflictException extends RuntimeException {

  private final String entityType;
  private final String entityId;
  private final Long clientVersion;
  private final Long currentVersion;

  public OptimisticLockConflictException(
      String entityType, Object entityId, Long clientVersion, Long currentVersion) {
    super(
        String.format(
            "%s was modified by another user. Your version: %d, current version: %d.",
            entityType, clientVersion, currentVersion));
    this.entityType = entityType;
    this.entityId = entityId != null ? entityId.toString() : "unknown";
    this.clientVersion = clientVersion;
    this.currentVersion = currentVersion;
  }
}
