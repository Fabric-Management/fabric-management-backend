package com.fabricmanagement.common.infrastructure.scheduling;

import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The threads a periodic per-tenant job runs on, so that one tenant's long run neither delays the
 * other tenants nor holds up the application's shared scheduler thread (which every other
 * {@code @Scheduled} job also waits for).
 *
 * <p>A tenant is in at most one place at a time: while its previous run is queued or running, the
 * next scheduled hand-off for it is declined, so a slow tenant cannot pile runs up behind itself.
 * The queue is therefore never longer than the number of tenants.
 *
 * <p>Each job extends this with a name and its own parallelism setting, so that jobs neither share
 * workers nor lose track of which job a thread belongs to.
 */
public abstract class TenantRunExecutor {
  private final ThreadPoolExecutor pool;
  private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

  /**
   * @param name prefix of the worker thread names
   * @param parallelism how many tenants may run at once (at least one)
   */
  protected TenantRunExecutor(String name, int parallelism) {
    int threads = Math.max(1, parallelism);
    AtomicInteger sequence = new AtomicInteger();
    ThreadFactory factory =
        runnable -> {
          Thread thread = new Thread(runnable, name + "-" + sequence.incrementAndGet());
          thread.setDaemon(true);
          return thread;
        };
    this.pool =
        new ThreadPoolExecutor(
            threads, threads, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), factory);
    // Nothing runs between rounds, so nothing should stay alive for it either.
    this.pool.allowCoreThreadTimeOut(true);
  }

  /**
   * Hands this tenant's run over to a worker.
   *
   * @return {@code false} when the tenant's previous run has not finished or the executor is shut
   *     down; nothing was scheduled
   */
  public boolean submit(UUID tenantId, Runnable work) {
    if (!inFlight.add(tenantId)) return false;
    try {
      pool.execute(
          () -> {
            try {
              work.run();
            } finally {
              inFlight.remove(tenantId);
            }
          });
      return true;
    } catch (RejectedExecutionException shutDown) {
      inFlight.remove(tenantId);
      return false;
    }
  }

  /** Lets a run that is under way finish (a short while), then stops the workers. */
  @PreDestroy
  public void shutdown() {
    pool.shutdown();
    try {
      if (!pool.awaitTermination(15, TimeUnit.SECONDS)) pool.shutdownNow();
    } catch (InterruptedException interrupted) {
      pool.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
