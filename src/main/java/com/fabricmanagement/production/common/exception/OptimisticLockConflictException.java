package com.fabricmanagement.production.common.exception;

/**
 * @deprecated Use {@link
 *     com.fabricmanagement.common.infrastructure.web.exception.OptimisticLockConflictException}
 *     instead. This class exists only for backward compatibility during migration.
 */
@Deprecated(forRemoval = true)
public class OptimisticLockConflictException
    extends com.fabricmanagement.common.infrastructure.web.exception
        .OptimisticLockConflictException {

  public OptimisticLockConflictException(
      String entityType, Object entityId, Long clientVersion, Long currentVersion) {
    super(entityType, entityId, clientVersion, currentVersion);
  }
}
