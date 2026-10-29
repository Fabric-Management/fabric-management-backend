package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.CollectionReconciliationHealth;
import com.fabricmanagement.finance.collection.infra.repository.CollectionReconciliationHealthRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectionReconciliationHealthService {
  private final CollectionReconciliationHealthRepository health;
  private final CollectionCommandStore commands;
  private final Clock clock;

  @Transactional
  public void finish(boolean failed) {
    commands.lock();
    var state =
        health
            .findByTenantId(TenantContext.requireTenantId())
            .orElseGet(CollectionReconciliationHealth::create);
    state.finish(clock.instant(), failed);
    health.saveAndFlush(state);
  }

  @Transactional(readOnly = true)
  public boolean stale() {
    return health
        .findByTenantId(TenantContext.requireTenantId())
        .map(
            s ->
                s.isFailed()
                    || s.getCompletedAt() == null
                    || s.getCompletedAt().isBefore(clock.instant().minus(Duration.ofMinutes(10))))
        .orElse(true);
  }
}
