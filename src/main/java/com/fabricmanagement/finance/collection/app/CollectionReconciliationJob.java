package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.finance.collection.app.CollectionReconciliationScanService.Finding;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Repairs what events missed and bootstraps agreements accepted before the listener was deployed.
 * No cross-tenant DML.
 *
 * <p>The cost of a run does not grow with the tenant's history. It lists a cheap version stamp per
 * agreed order and has {@link CollectionReconciliationScanService} judge each against its
 * projection: orders that changed are read in full at once; orders that merely have not been read
 * for longer than their tier allows ({@link CollectionReconciliationPolicy}) are read afterwards,
 * longest waiting first; the rest cost nothing, or one cheap update of their check time. At most
 * {@code read-budget} orders are read per run, so what does not fit waits for the next run instead
 * of stretching this one. Projections whose order is no longer listed are found in the same pass,
 * so a vanished source stays visibly unknown.
 *
 * <p>An order is read without waiting for its writers: reading holds the finance tenant lock, and
 * waiting on an order meanwhile would keep every other finance command of the tenant waiting too.
 * An order a writer holds is left for the next run. That only makes the run unhealthy when the
 * order is known to have changed; one that was merely due loses nothing by waiting.
 *
 * <p>Tenants run on {@link CollectionReconciliationExecutor}, one run per tenant at a time, and
 * only on the instance that holds {@link CollectionReconciliationLease}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollectionReconciliationJob {
  private static final UUID FIRST = new UUID(0, 0);
  private static final int PAGE = 200;
  private static final Comparator<Finding> OLDEST_FIRST =
      Comparator.comparing(
          Finding::readAt, Comparator.nullsFirst(Comparator.<Instant>naturalOrder()));
  private final TenantQueryPort tenants;
  private final CollectionSourcePort source;
  private final CollectionProjectionService projections;
  private final CollectionReconciliationScanService scan;
  private final CollectionPaymentObservationService payments;
  private final CollectionReconciliationHealthService health;
  private final CollectionReconciliationPolicy policy;
  private final CollectionReconciliationExecutor executor;
  private final CollectionReconciliationLease lease;
  private final Clock clock;

  /** Only hands the tenants over: a tenant still busy with its last run is left alone. */
  @Scheduled(fixedDelayString = "${finance.collections.reconcile-ms:300000}")
  public void reconcile() {
    for (var tenant : tenants.findAllActiveTenants())
      if (!executor.submit(tenant.id(), () -> reconcile(tenant)))
        log.debug("Collection reconciliation of tenant {} is still running", tenant.id());
  }

  private void reconcile(TenantReference tenant) {
    try {
      var held = lease.tryAcquire(tenant.id());
      if (held.isEmpty()) {
        log.debug("Collection reconciliation of tenant {} runs elsewhere", tenant.id());
        return;
      }
      try (var ignored = held.get()) {
        TenantContext.executeInTenantContext(
            tenant.id(),
            () -> {
              TenantContext.setCurrentTenantUid(tenant.uid());
              TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
              reconcileTenant();
            });
      }
    } catch (RuntimeException failure) {
      log.error("Collection reconciliation failed for tenant {}", tenant.id(), failure);
    }
  }

  private boolean attempt(UUID id, Runnable work) {
    try {
      work.run();
      return false;
    } catch (CollectionRuleException busy) {
      if (!"COLLECTION_SOURCE_BUSY".equals(busy.getErrorCode())) return failed(id, busy);
      // A writer holds the order; the next run reads it, and its checked-at time stays honest.
      log.warn(
          "Collection reconciliation skipped busy order {} for tenant {}",
          id,
          TenantContext.requireTenantId());
      return true;
    } catch (RuntimeException failure) {
      return failed(id, failure);
    }
  }

  /** How reading one order, without waiting for its writers, ended. */
  private enum Read {
    DONE,
    BUSY,
    FAILED
  }

  private Read read(UUID id) {
    try {
      return projections.synchronizeIfFree(id) ? Read.DONE : Read.BUSY;
    } catch (CollectionRuleException refused) {
      // A writer that won the order after the check is just as busy.
      if ("COLLECTION_SOURCE_BUSY".equals(refused.getErrorCode())) return Read.BUSY;
      failed(id, refused);
      return Read.FAILED;
    } catch (RuntimeException failure) {
      failed(id, failure);
      return Read.FAILED;
    }
  }

  /**
   * Whether the run is unhealthy because of this read. A busy order is read by the next run and its
   * checked-at time stays honest; it only counts against the run when it is known to have changed,
   * because then the recorded projection is out of date until that run.
   */
  private boolean unresolved(UUID id, Read outcome, boolean changed) {
    return switch (outcome) {
      case DONE -> false;
      case FAILED -> true;
      case BUSY -> {
        if (changed)
          log.warn(
              "Collection reconciliation skipped busy changed order {} for tenant {}",
              id,
              TenantContext.requireTenantId());
        else
          log.debug(
              "Collection reconciliation left busy due order {} for tenant {}",
              id,
              TenantContext.requireTenantId());
        yield changed;
      }
    };
  }

  private boolean failed(UUID id, RuntimeException failure) {
    log.error(
        "Collection reconciliation failed for tenant {} resource {}",
        TenantContext.requireTenantId(),
        id,
        failure);
    return true;
  }

  public void reconcileTenant() {
    boolean failed = true;
    try {
      failed = reconcileSources();
    } finally {
      health.finish(failed);
    }
  }

  private boolean reconcileSources() {
    UUID tenant = TenantContext.requireTenantId();
    var tiers = policy.tiers(clock.instant());
    var reads = new Reads(policy.readBudget());
    boolean failed = false;
    UUID after = FIRST;
    Instant listedAt;
    while (true) {
      // Taken before the listing, so a change that lands while the page is processed is never
      // vouched for by a time that came after it.
      listedAt = clock.instant();
      var stamps = source.stamps(tenant, after, PAGE);
      if (stamps.isEmpty()) break;
      failed |= handle(scan.triage(after, stamps, tiers), listedAt, reads);
      after = stamps.getLast().orderId();
    }
    // Projections past the last listed order have no source either; the empty listing above is
    // what vouches for them, at the time it was taken.
    while (true) {
      var rest = scan.unlistedAfter(after, tiers);
      if (rest.isEmpty()) break;
      failed |= handle(rest, listedAt, reads);
      after = rest.getLast().orderId();
    }
    failed |= readDue(reads, tenant);
    if (reads.changedLeftOver > 0) {
      // Real differences that this run had no room to read: the data is stale until the next run.
      log.warn(
          "Collection reconciliation read budget of {} reached for tenant {}: {} changed orders"
              + " wait for the next run",
          policy.readBudget(),
          tenant,
          reads.changedLeftOver);
      failed = true;
    }
    failed |= observeVoidedPayments();
    return failed;
  }

  private boolean handle(List<Finding> findings, Instant listedAt, Reads reads) {
    boolean failed = false;
    var current = new ArrayList<UUID>();
    for (var finding : findings) {
      UUID id = finding.orderId();
      switch (finding.verdict()) {
        case CHANGED -> {
          if (reads.take()) failed |= unresolved(id, read(id), true);
        }
        case DUE -> reads.defer(finding);
        // Compared, not read: they stand verified as of the listing (logged under the first id).
        case CURRENT_HOT -> current.add(id);
        case CURRENT_COLD -> {}
      }
    }
    if (!current.isEmpty())
      failed |= attempt(current.getFirst(), () -> projections.confirmUnchanged(current, listedAt));
    return failed;
  }

  /** The orders that were only due, longest waiting first, with what the budget has left. */
  private boolean readDue(Reads reads, UUID tenant) {
    boolean failed = false;
    var batch = reads.dueWithinBudget();
    for (var finding : batch) {
      UUID id = finding.orderId();
      failed |= unresolved(id, read(id), false);
    }
    long waiting = reads.dueSeen - batch.size();
    if (waiting > 0)
      log.warn(
          "Collection reconciliation read budget of {} reached for tenant {}: {} orders that are"
              + " due wait for the next run",
          policy.readBudget(),
          tenant,
          waiting);
    return failed;
  }

  private boolean observeVoidedPayments() {
    boolean failed = false;
    UUID after = FIRST;
    while (true) {
      var batch = scan.voidedPayments(after);
      if (batch.isEmpty()) break;
      for (var id : batch) failed |= attempt(id, () -> payments.observe(id));
      after = batch.getLast();
    }
    return failed;
  }

  /**
   * What one run may still read. Changed orders are read as they are met and use the budget first;
   * due ones are only remembered, the longest-waiting {@code budget} of them, so memory stays
   * bounded however many are due, and are read at the end with whatever is left.
   */
  private static final class Reads {
    private final int budget;
    private int left;
    private long dueSeen;
    private int changedLeftOver;
    private final PriorityQueue<Finding> due = new PriorityQueue<>(OLDEST_FIRST.reversed());

    Reads(int budget) {
      this.budget = budget;
      this.left = budget;
    }

    /** Claims one read for a changed order; says no, and counts it, when the budget is spent. */
    boolean take() {
      if (left == 0) {
        changedLeftOver++;
        return false;
      }
      left--;
      return true;
    }

    void defer(Finding finding) {
      dueSeen++;
      due.add(finding);
      // The head is the one read most recently: the least in need of a read.
      if (due.size() > budget) due.poll();
    }

    List<Finding> dueWithinBudget() {
      var oldestFirst = new ArrayList<>(due);
      oldestFirst.sort(OLDEST_FIRST);
      return oldestFirst.subList(0, Math.min(left, oldestFirst.size()));
    }
  }
}
