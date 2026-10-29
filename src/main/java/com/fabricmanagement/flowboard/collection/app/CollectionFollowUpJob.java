package com.fabricmanagement.flowboard.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A dated scan that repairs what events missed; it is not a posting scheduler and not how follow-up
 * work normally arrives (that is the ledger's change events).
 *
 * <p>It looks at two things only, so its cost follows what is open and not everything the tenant
 * ever agreed: the obligations that are open and due, in case one never got its case; and the cases
 * that are open, in case the ledger has since closed their obligation. It reads the ledger's
 * recorded state and leaves a case that already shows it alone. At most {@code repair-budget} cases
 * are changed per run, so a backlog is worked off over several runs instead of stretching one;
 * because a repaired case drops out of the work, the cases behind it are reached by the next run.
 * Whatever is left over keeps the queue from being certified.
 *
 * <p>Tenants run on {@link CollectionFollowUpExecutor}, one run per tenant at a time, and only on
 * the instance that holds {@link CollectionFollowUpLease}. Every repair is a transaction of its
 * own, so the finance tenant lock is held for one case at a time, never across the scan.
 */
@Component
@Slf4j
public class CollectionFollowUpJob {
  private static final UUID FIRST = new UUID(0, 0);
  private static final int PAGE = 100;
  private final TenantQueryPort tenants;
  private final CollectionFollowUpPort source;
  private final CollectionFollowUpService service;
  private final CollectionFollowUpHealthService health;
  private final CollectionFollowUpExecutor executor;
  private final CollectionFollowUpLease lease;
  private final int repairBudget;

  public CollectionFollowUpJob(
      TenantQueryPort tenants,
      CollectionFollowUpPort source,
      CollectionFollowUpService service,
      CollectionFollowUpHealthService health,
      CollectionFollowUpExecutor executor,
      CollectionFollowUpLease lease,
      @Value("${flowboard.collections.repair-budget:500}") int repairBudget) {
    this.tenants = tenants;
    this.source = source;
    this.service = service;
    this.health = health;
    this.executor = executor;
    this.lease = lease;
    this.repairBudget = Math.max(1, repairBudget);
  }

  /** Only hands the tenants over: a tenant still busy with its last scan is left alone. */
  @Scheduled(fixedDelayString = "${flowboard.collections.scan-ms:300000}")
  public void scan() {
    for (var tenant : tenants.findAllActiveTenants())
      if (!executor.submit(tenant.id(), () -> scan(tenant)))
        log.debug("Collection follow-up scan of tenant {} is still running", tenant.id());
  }

  private void scan(TenantReference tenant) {
    try {
      var held = lease.tryAcquire(tenant.id());
      if (held.isEmpty()) {
        log.debug("Collection follow-up scan of tenant {} runs elsewhere", tenant.id());
        return;
      }
      try (var ignored = held.get()) {
        TenantContext.executeInTenantContext(
            tenant.id(),
            () -> {
              TenantContext.setCurrentTenantUid(tenant.uid());
              TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
              scanTenant();
            });
      }
    } catch (RuntimeException e) {
      log.error("Collection follow-up scan failed for tenant {}", tenant.id(), e);
    }
  }

  public void scanTenant() {
    var budget = new Budget(repairBudget);
    boolean failed = false;
    // One failing phase never keeps the other from running, but it keeps the queue uncertified.
    failed |= phase("open and due obligations", () -> repairOpenAndDue(budget));
    failed |= phase("open cases", () -> repairClosed(budget));
    health.finish(TenantContext.requireTenantId(), failed);
  }

  private boolean phase(String name, BooleanSupplier work) {
    try {
      return work.getAsBoolean();
    } catch (RuntimeException e) {
      log.error("Collection follow-up discovery of {} failed", name, e);
      return true;
    }
  }

  /** Open, due obligations: each must have its case. */
  private boolean repairOpenAndDue(Budget budget) {
    boolean failed = false;
    UUID after = FIRST;
    while (true) {
      var ids = source.discover(after, PAGE);
      if (ids.isEmpty()) return failed;
      for (UUID id : ids) {
        if (budget.spent()) return exhausted();
        failed |= attempt(id, budget, () -> service.repair(id));
      }
      after = ids.getLast();
    }
  }

  /** Open cases: each whose obligation the ledger has closed must be closed too. */
  private boolean repairClosed(Budget budget) {
    boolean failed = false;
    UUID after = FIRST;
    while (true) {
      var ids = service.openObligations(after, PAGE);
      if (ids.isEmpty()) return failed;
      for (UUID id : source.closed(ids)) {
        if (budget.spent()) return exhausted();
        failed |= attempt(id, budget, () -> service.repair(id));
      }
      after = ids.getLast();
    }
  }

  /**
   * @return {@code true} when the repair failed; one failing case never stops the others
   */
  private boolean attempt(UUID id, Budget budget, BooleanSupplier repair) {
    try {
      if (repair.getAsBoolean()) budget.use();
      return false;
    } catch (RuntimeException e) {
      log.error("Collection follow-up refresh failed for obligation {}", id, e);
      return true;
    }
  }

  private boolean exhausted() {
    log.warn(
        "Collection follow-up repair budget of {} reached for tenant {}: the rest waits for the"
            + " next scan",
        repairBudget,
        TenantContext.requireTenantId());
    return true;
  }

  /** How many more cases this scan may change. */
  private static final class Budget {
    private int left;

    Budget(int total) {
      this.left = total;
    }

    boolean spent() {
      return left == 0;
    }

    void use() {
      left--;
    }
  }
}
