package com.fabricmanagement.platform.subscription.app;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Clear derived entitlement decisions only after the selection transaction commits. */
@Component
@RequiredArgsConstructor
public class EvaluationAccessInvalidator {
  private final CacheManager caches;

  public void afterCommit() {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            for (String name :
                java.util.List.of("subscription-check", "feature-check", "policy-decision")) {
              var cache = caches.getCache(name);
              if (cache != null) cache.clear();
            }
          }
        });
  }
}
