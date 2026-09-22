package com.fabricmanagement.flowboard.decision.app;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class DecisionProjectionRebuildState {
  private final ConcurrentHashMap<UUID, AtomicInteger> running = new ConcurrentHashMap<>();

  public void started(UUID tenant) {
    running.compute(
        tenant,
        (ignored, count) -> {
          if (count == null) return new AtomicInteger(1);
          count.incrementAndGet();
          return count;
        });
  }

  public void finished(UUID tenant) {
    running.computeIfPresent(
        tenant, (ignored, count) -> count.decrementAndGet() == 0 ? null : count);
  }

  public boolean isRunning(UUID tenant) {
    AtomicInteger count = running.get(tenant);
    return count != null && count.get() > 0;
  }
}
