package com.fabricmanagement.finance.collection.app;

import static com.fabricmanagement.finance.collection.app.CollectionReconciliationScanService.Verdict.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.scheduling.TenantRunLease.Hold;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.finance.collection.app.CollectionReconciliationScanService.Finding;
import com.fabricmanagement.finance.collection.app.CollectionReconciliationScanService.Verdict;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort.SourceStamp;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CollectionReconciliationJobTest {
  static final UUID FIRST = new UUID(0, 0);
  final UUID tenant = UUID.randomUUID();
  final Instant at = Instant.parse("2026-10-23T08:00:00Z");
  final Clock clock = Clock.fixed(at, ZoneOffset.UTC);
  final Map<UUID, Finding> verdicts = new HashMap<>();
  TenantQueryPort tenants;
  CollectionSourcePort source;
  CollectionProjectionService projection;
  CollectionReconciliationScanService scan;
  CollectionPaymentObservationService observer;
  CollectionReconciliationHealthService health;
  CollectionReconciliationExecutor executor;
  CollectionReconciliationLease lease;
  CollectionReconciliationJob job;

  @BeforeEach
  void setup() {
    tenants = mock(TenantQueryPort.class);
    source = mock(CollectionSourcePort.class);
    projection = mock(CollectionProjectionService.class);
    scan = mock(CollectionReconciliationScanService.class);
    observer = mock(CollectionPaymentObservationService.class);
    health = mock(CollectionReconciliationHealthService.class);
    executor = mock(CollectionReconciliationExecutor.class);
    lease = mock(CollectionReconciliationLease.class);
    jobWithBudget(500);
    // A read finds the order free unless a test says a writer holds it.
    when(projection.synchronizeIfFree(any())).thenReturn(true);
    // Whatever the test does not say about an order, it is current and cold: it costs nothing.
    when(scan.triage(any(), any(), any()))
        .thenAnswer(
            call ->
                call.<List<SourceStamp>>getArgument(1).stream()
                    .map(
                        stamp ->
                            verdicts.getOrDefault(
                                stamp.orderId(), new Finding(stamp.orderId(), CURRENT_COLD, null)))
                    .toList());
  }

  private void jobWithBudget(int budget) {
    var policy = new CollectionReconciliationPolicy(budget, 3_600_000, 21_600_000, 30);
    job =
        new CollectionReconciliationJob(
            tenants, source, projection, scan, observer, health, policy, executor, lease, clock);
  }

  private static SourceStamp stamp(UUID order) {
    return new SourceStamp(order, 1, 1, UUID.randomUUID(), false);
  }

  private void verdict(UUID order, Verdict verdict) {
    verdicts.put(order, new Finding(order, verdict, null));
  }

  private void verdict(UUID order, Verdict verdict, Instant readAt) {
    verdicts.put(order, new Finding(order, verdict, readAt));
  }

  /** The source lists these stamps in one page. */
  private void listed(UUID... orders) {
    var stamps = Arrays.stream(orders).map(CollectionReconciliationJobTest::stamp).toList();
    when(source.stamps(eq(tenant), any(), eq(200))).thenReturn(stamps, List.of());
  }

  private void run() {
    TenantContext.executeInTenantContext(tenant, job::reconcileTenant);
  }

  private static Collection<UUID> exactly(UUID... orders) {
    return argThat((Collection<UUID> given) -> Set.copyOf(given).equals(Set.of(orders)));
  }

  @Test
  void anOrderThatMatchesAndIsColdCostsNothingAndTheRunIsHealthy() {
    UUID cold = UUID.randomUUID();
    listed(cold);
    verdict(cold, CURRENT_COLD);
    run();
    verify(projection, never()).synchronizeIfFree(any());
    verify(projection, never()).confirmUnchanged(any(), any());
    verify(health).finish(false);
  }

  @Test
  void changedOrdersAreReadAtOnceAndTheOthersAreNot() {
    UUID moved = UUID.randomUUID(), other = UUID.randomUUID();
    listed(moved, other);
    verdict(moved, CHANGED);
    run();
    verify(projection).synchronizeIfFree(moved);
    verify(projection, never()).synchronizeIfFree(other);
    verify(health).finish(false);
  }

  @Test
  void hotOrdersThatMatchAreConfirmedAsCheckedAtTheListingTimeInOneCallPerPage() {
    UUID hot = UUID.randomUUID(), alsoHot = UUID.randomUUID(), moved = UUID.randomUUID();
    listed(hot, moved, alsoHot);
    verdict(hot, CURRENT_HOT);
    verdict(alsoHot, CURRENT_HOT);
    verdict(moved, CHANGED);
    run();
    verify(projection).confirmUnchanged(exactly(hot, alsoHot), eq(at));
    verify(projection, times(1)).confirmUnchanged(any(), any());
  }

  @Test
  void eachListedPageConfirmsItsOwnHotOrdersAndIsJudgedWithTheRunsTiers() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    var first = List.of(stamp(a), stamp(b));
    var second = List.of(stamp(c));
    when(source.stamps(tenant, FIRST, 200)).thenReturn(first);
    when(source.stamps(tenant, b, 200)).thenReturn(second);
    when(source.stamps(tenant, c, 200)).thenReturn(List.of());
    verdict(a, CURRENT_HOT);
    verdict(c, CURRENT_HOT);
    run();
    var tiers = new CollectionReconciliationPolicy(500, 3_600_000, 21_600_000, 30).tiers(at);
    verify(scan).triage(FIRST, first, tiers);
    verify(scan).triage(b, second, tiers);
    verify(projection).confirmUnchanged(exactly(a), eq(at));
    verify(projection).confirmUnchanged(exactly(c), eq(at));
  }

  @Test
  void aPageWithNothingHotToConfirmConfirmsNothing() {
    UUID moved = UUID.randomUUID();
    listed(moved);
    verdict(moved, CHANGED);
    run();
    verify(projection, never()).confirmUnchanged(any(), any());
  }

  @Test
  void aConfirmationThatCouldNotBeRecordedLeavesTheRunUnhealthy() {
    UUID hot = UUID.randomUUID();
    listed(hot);
    verdict(hot, CURRENT_HOT);
    doThrow(new IllegalStateException("write refused"))
        .when(projection)
        .confirmUnchanged(any(), any());
    run();
    verify(health).finish(true);
  }

  @Test
  void dueOrdersAreReadAfterEveryChangedOrderEvenWhenListedBeforeIt() {
    UUID due = UUID.randomUUID(), moved = UUID.randomUUID();
    listed(due, moved);
    verdict(due, DUE, at.minus(Duration.ofHours(2)));
    verdict(moved, CHANGED);
    run();
    InOrder order = inOrder(projection);
    order.verify(projection).synchronizeIfFree(moved);
    order.verify(projection).synchronizeIfFree(due);
    verify(health).finish(false);
  }

  @Test
  void whenTheBudgetIsShortTheLongestWaitingDueOrdersAreReadFirst() {
    jobWithBudget(2);
    UUID never = UUID.randomUUID(), oldest = UUID.randomUUID();
    UUID middle = UUID.randomUUID(), newest = UUID.randomUUID();
    listed(newest, middle, never, oldest);
    verdict(never, DUE, null);
    verdict(oldest, DUE, at.minus(Duration.ofHours(5)));
    verdict(middle, DUE, at.minus(Duration.ofHours(3)));
    verdict(newest, DUE, at.minus(Duration.ofHours(2)));
    run();
    verify(projection).synchronizeIfFree(never);
    verify(projection).synchronizeIfFree(oldest);
    verify(projection, never()).synchronizeIfFree(middle);
    verify(projection, never()).synchronizeIfFree(newest);
    // Waiting orders are not stale data: they match their projection. The run is healthy.
    verify(health).finish(false);
  }

  @Test
  void changedOrdersUseTheBudgetFirstAndWhatDoesNotFitMakesTheRunUnhealthy() {
    jobWithBudget(2);
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    UUID due = UUID.randomUUID();
    listed(a, b, c, due);
    verdict(a, CHANGED);
    verdict(b, CHANGED);
    verdict(c, CHANGED);
    verdict(due, DUE, null);
    run();
    verify(projection).synchronizeIfFree(a);
    verify(projection).synchronizeIfFree(b);
    verify(projection, never()).synchronizeIfFree(c);
    verify(projection, never()).synchronizeIfFree(due);
    // A real difference was left unread: the data is stale until the next run.
    verify(health).finish(true);
  }

  @Test
  void aChangedOrderAWriterHoldsIsSkippedForTheNextRunAndTheRunIsNotHealthy() {
    UUID busy = UUID.randomUUID(), free = UUID.randomUUID();
    listed(busy, free);
    verdict(busy, CHANGED);
    verdict(free, CHANGED);
    when(projection.synchronizeIfFree(busy)).thenReturn(false);
    run();
    verify(projection).synchronizeIfFree(free);
    // The order moved and was not read, so the recorded projection is out of date until next run.
    verify(health).finish(true);
  }

  @Test
  void aWriterThatWinsAChangedOrderAfterTheCheckIsBusyToo() {
    UUID busy = UUID.randomUUID(), free = UUID.randomUUID();
    listed(busy, free);
    verdict(busy, CHANGED);
    verdict(free, CHANGED);
    doThrow(CollectionRuleException.conflict("COLLECTION_SOURCE_BUSY", "The order is being edited"))
        .when(projection)
        .synchronizeIfFree(busy);
    run();
    verify(projection).synchronizeIfFree(free);
    verify(health).finish(true);
  }

  @Test
  void aDueOrderAWriterHoldsIsLeftForTheNextRunAndTheRunStaysHealthy() {
    UUID busy = UUID.randomUUID(), free = UUID.randomUUID();
    listed(busy, free);
    verdict(busy, DUE, at.minus(Duration.ofHours(5)));
    verdict(free, DUE, at.minus(Duration.ofHours(3)));
    when(projection.synchronizeIfFree(busy)).thenReturn(false);
    run();
    verify(projection).synchronizeIfFree(busy);
    verify(projection).synchronizeIfFree(free);
    // Both match their projection; the busy one has only not been looked at again yet.
    verify(health).finish(false);
  }

  @Test
  void aBusyDueOrderDoesNotHideAFailureOfAnotherOne() {
    UUID busy = UUID.randomUUID(), broken = UUID.randomUUID();
    listed(busy, broken);
    verdict(busy, DUE, at.minus(Duration.ofHours(5)));
    verdict(broken, DUE, at.minus(Duration.ofHours(3)));
    when(projection.synchronizeIfFree(busy)).thenReturn(false);
    doThrow(CollectionRuleException.conflict("COLLECTION_SOURCE_INCOMPLETE", "No proof"))
        .when(projection)
        .synchronizeIfFree(broken);
    run();
    verify(health).finish(true);
  }

  @Test
  void ordersAreNeverReadWithAWaitingCall() {
    UUID moved = UUID.randomUUID(), due = UUID.randomUUID();
    listed(moved, due);
    verdict(moved, CHANGED);
    verdict(due, DUE, at.minus(Duration.ofHours(2)));
    run();
    verify(projection).synchronizeIfFree(moved);
    verify(projection).synchronizeIfFree(due);
    verify(projection, never()).synchronize(any());
  }

  @Test
  void recoveryContinuesPastOneFailingSourceAndStillObservesVoidedPayments() {
    UUID order = UUID.randomUUID(), orphan = UUID.randomUUID(), payment = UUID.randomUUID();
    listed(order);
    verdict(order, CHANGED);
    when(scan.unlistedAfter(any(), any()))
        .thenReturn(List.of(new Finding(orphan, CHANGED, null)), List.of());
    when(scan.voidedPayments(any())).thenReturn(List.of(payment), List.of());
    doThrow(new IllegalStateException("Source temporarily unavailable"))
        .when(projection)
        .synchronizeIfFree(order);
    run();
    verify(projection).synchronizeIfFree(order);
    verify(projection).synchronizeIfFree(orphan);
    verify(observer).observe(payment);
    verify(health).finish(true);
  }

  @Test
  void aProjectionInsideAPageRangeThatIsNotListedIsReadAndDoesNotMoveTheListingCursor() {
    UUID agreed = UUID.randomUUID(), orphan = UUID.randomUUID();
    when(source.stamps(tenant, FIRST, 200)).thenReturn(List.of(stamp(agreed)));
    when(source.stamps(tenant, agreed, 200)).thenReturn(List.of());
    // The scan reports the listed order and, found in the same range, one that is not listed.
    doReturn(List.of(new Finding(agreed, CURRENT_COLD, null), new Finding(orphan, CHANGED, null)))
        .when(scan)
        .triage(any(), any(), any());
    run();
    verify(projection).synchronizeIfFree(orphan);
    verify(source).stamps(tenant, agreed, 200);
    verify(health).finish(false);
  }

  @Test
  void projectionsPastTheLastListedOrderAreContinuedFromTheLastListedOne() {
    UUID agreed = UUID.randomUUID(), tail = UUID.randomUUID(), laterTail = UUID.randomUUID();
    listed(agreed);
    when(scan.unlistedAfter(eq(agreed), any()))
        .thenReturn(List.of(new Finding(tail, CHANGED, null)));
    when(scan.unlistedAfter(eq(tail), any()))
        .thenReturn(List.of(new Finding(laterTail, CHANGED, null)));
    when(scan.unlistedAfter(eq(laterTail), any())).thenReturn(List.of());
    run();
    verify(projection).synchronizeIfFree(tail);
    verify(projection).synchronizeIfFree(laterTail);
    verify(health).finish(false);
  }

  @Test
  void whenNothingIsAgreedEveryProjectionIsUnlistedAndTheScanStartsFromTheBeginning() {
    UUID orphan = UUID.randomUUID();
    when(source.stamps(eq(tenant), any(), eq(200))).thenReturn(List.of());
    when(scan.unlistedAfter(eq(FIRST), any()))
        .thenReturn(List.of(new Finding(orphan, CHANGED, null)));
    when(scan.unlistedAfter(eq(orphan), any())).thenReturn(List.of());
    run();
    verify(projection).synchronizeIfFree(orphan);
  }

  @Test
  void anUnlistedOrphanThatIsHotAndUnchangedIsConfirmedAtTheTimeOfTheEmptyListing() {
    UUID orphan = UUID.randomUUID();
    when(source.stamps(eq(tenant), any(), eq(200))).thenReturn(List.of());
    when(scan.unlistedAfter(eq(FIRST), any()))
        .thenReturn(List.of(new Finding(orphan, CURRENT_HOT, at)));
    when(scan.unlistedAfter(eq(orphan), any())).thenReturn(List.of());
    run();
    verify(projection).confirmUnchanged(exactly(orphan), eq(at));
    verify(projection, never()).synchronizeIfFree(any());
  }

  @Test
  void aRunThatBlowsUpIsStillRecordedAsFailedAndTheFailureReachesTheCaller() {
    when(source.stamps(eq(tenant), any(), eq(200))).thenThrow(new IllegalStateException("down"));
    assertThatThrownBy(this::run).isInstanceOf(IllegalStateException.class);
    verify(health).finish(true);
  }

  // --- hand-off: executor and lease ---------------------------------------------------------

  private TenantReference tenantNamed(UUID id) {
    return new TenantReference(id, "uid-" + id, "name", "type");
  }

  /** The executor runs the handed-over work straight away, on the calling thread. */
  private void executorRunsInline() {
    when(executor.submit(any(), any()))
        .thenAnswer(
            call -> {
              call.<Runnable>getArgument(1).run();
              return true;
            });
  }

  @Test
  void theScheduledRunOnlyHandsEachTenantOverAndSkipsOneStillRunning() {
    UUID running = UUID.randomUUID(), free = UUID.randomUUID();
    when(tenants.findAllActiveTenants())
        .thenReturn(List.of(tenantNamed(running), tenantNamed(free)));
    when(executor.submit(eq(running), any())).thenReturn(false);
    when(executor.submit(eq(free), any())).thenReturn(true);
    job.reconcile();
    verify(executor).submit(eq(running), any());
    verify(executor).submit(eq(free), any());
    // Handing over reads nothing and holds no lease: that happens on the worker.
    verifyNoInteractions(source, lease, health);
  }

  @Test
  void aHandedOverTenantIsReconciledInItsOwnContextWhileHoldingItsLease() throws Exception {
    UUID id = UUID.randomUUID();
    Connection connection = mock(Connection.class);
    when(tenants.findAllActiveTenants()).thenReturn(List.of(tenantNamed(id)));
    when(lease.tryAcquire(id)).thenReturn(Optional.of(new Hold(connection)));
    when(source.stamps(eq(id), any(), eq(200))).thenReturn(List.of());
    executorRunsInline();
    job.reconcile();
    verify(source).stamps(eq(id), any(), eq(200));
    verify(health).finish(false);
    // The lease is let go once the run is over, which ends its transaction.
    verify(connection).rollback();
    verify(connection).close();
  }

  @Test
  void aTenantWhoseLeaseIsHeldElsewhereIsNotReconciledHere() {
    UUID id = UUID.randomUUID();
    when(tenants.findAllActiveTenants()).thenReturn(List.of(tenantNamed(id)));
    when(lease.tryAcquire(id)).thenReturn(Optional.empty());
    executorRunsInline();
    job.reconcile();
    verifyNoInteractions(source, health, projection);
  }

  @Test
  void theLeaseIsReleasedAndTheOtherTenantsStillRunWhenOneTenantsRunBlowsUp() throws Exception {
    UUID broken = UUID.randomUUID(), fine = UUID.randomUUID();
    Connection brokenConnection = mock(Connection.class);
    Connection fineConnection = mock(Connection.class);
    when(tenants.findAllActiveTenants())
        .thenReturn(List.of(tenantNamed(broken), tenantNamed(fine)));
    when(lease.tryAcquire(broken)).thenReturn(Optional.of(new Hold(brokenConnection)));
    when(lease.tryAcquire(fine)).thenReturn(Optional.of(new Hold(fineConnection)));
    when(source.stamps(eq(broken), any(), eq(200))).thenThrow(new IllegalStateException("down"));
    when(source.stamps(eq(fine), any(), eq(200))).thenReturn(List.of());
    executorRunsInline();
    job.reconcile();
    verify(source).stamps(eq(fine), any(), eq(200));
    verify(brokenConnection).rollback();
    verify(brokenConnection).close();
    verify(fineConnection).rollback();
  }
}
