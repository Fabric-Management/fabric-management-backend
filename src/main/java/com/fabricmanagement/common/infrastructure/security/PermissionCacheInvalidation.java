package com.fabricmanagement.common.infrastructure.security;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Invalidates decisions only when the changed membership is visible to other requests. */
@Component
@RequiredArgsConstructor
public class PermissionCacheInvalidation {

  private final CacheManager caches;

  public void evictMemberAfterCommit(UUID tenantId, UUID userId) {
    afterCommit(() -> cache().evict(tenantId + "_" + userId));
  }

  public void evictRolesAfterCommit(UUID tenantId) {
    afterCommit(() -> java.util.Objects.requireNonNull(caches.getCache("roles")).evict(tenantId));
  }

  private Cache cache() {
    return java.util.Objects.requireNonNull(caches.getCache("permissions"));
  }

  private void afterCommit(Runnable invalidation) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            invalidation.run();
          }
        });
  }
}
