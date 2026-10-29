package com.fabricmanagement.flowboard.collection.app;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.scheduling.TenantRunLease.Hold;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollectionFollowUpJobTest {
  static final UUID FIRST = new UUID(0, 0);
  final UUID tenant = UUID.randomUUID();
  final UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
  final UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
  final UUID c = UUID.fromString("00000000-0000-0000-0000-000000000003");
  TenantQueryPort tenants;
  CollectionFollowUpPort source;
  CollectionFollowUpService service;
  CollectionFollowUpHealthService health;
  CollectionFollowUpExecutor executor;
  CollectionFollowUpLease lease;
  CollectionFollowUpJob job;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    tenants = mock(TenantQueryPort.class);
    source = mock(CollectionFollowUpPort.class);
    service = mock(CollectionFollowUpService.class);
    health = mock(CollectionFollowUpHealthService.class);
    executor = mock(CollectionFollowUpExecutor.class);
    lease = mock(CollectionFollowUpLease.class);
    jobWithBudget(500);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private void jobWithBudget(int budget) {
    job = new CollectionFollowUpJob(tenants, source, service, health, executor, lease, budget);
  }

  @Test
  void failedResourceDoesNotStarveLaterWorkAndMarksTheQueueStale() {
    when(source.discover(FIRST, 100)).thenReturn(List.of(a, b));
    when(source.discover(b, 100)).thenReturn(List.of());
    doThrow(new IllegalStateException("worker interrupted")).when(service).repair(a);

    job.scanTenant();

    verify(service).repair(b);
    verify(health).finish(tenant, true);
  }

  @Test
  void aCaseAlreadyInLineCostsNoBudgetSoEveryOpenDueObligationIsChecked() {
    jobWithBudget(1);
    when(source.discover(FIRST, 100)).thenReturn(List.of(a, b, c));
    when(source.discover(c, 100)).thenReturn(List.of());
    when(service.repair(any())).thenReturn(false);

    job.scanTenant();

    verify(service).repair(a);
    verify(service).repair(b);
    verify(service).repair(c);
    verify(health).finish(tenant, false);
  }

  @Test
  void theBudgetStopsTheChangesAndKeepsTheQueueFromBeingCertified() {
    jobWithBudget(2);
    when(source.discover(FIRST, 100)).thenReturn(List.of(a, b, c));
    when(service.repair(any())).thenReturn(true);

    job.scanTenant();

    verify(service).repair(a);
    verify(service).repair(b);
    verify(service, never()).repair(c);
    verify(health).finish(tenant, true);
  }

  @Test
  void aBudgetSpentOnTheLastChangeLeavesNothingBehindAndTheQueueCertified() {
    jobWithBudget(2);
    when(source.discover(FIRST, 100)).thenReturn(List.of(a, b));
    when(source.discover(b, 100)).thenReturn(List.of());
    when(service.repair(any())).thenReturn(true);

    job.scanTenant();

    verify(health).finish(tenant, false);
  }

  @Test
  void discoveryContinuesFromTheLastIdOfEachPage() {
    when(source.discover(FIRST, 100)).thenReturn(List.of(a));
    when(source.discover(a, 100)).thenReturn(List.of(b));
    when(source.discover(b, 100)).thenReturn(List.of());

    job.scanTenant();

    verify(service).repair(a);
    verify(service).repair(b);
    verify(health).finish(tenant, false);
  }

  @Test
  void openCasesTheLedgerHasClosedAreRepairedAndTheOthersAreNotTouched() {
    when(service.openObligations(FIRST, 100)).thenReturn(List.of(a, b));
    when(service.openObligations(b, 100)).thenReturn(List.of());
    when(source.closed(List.of(a, b))).thenReturn(Set.of(b));
    when(service.repair(b)).thenReturn(true);

    job.scanTenant();

    verify(service).repair(b);
    verify(service, never()).repair(a);
    verify(health).finish(tenant, false);
  }

  @Test
  void closingsShareTheBudgetWithTheRepairsOfMissingCases() {
    jobWithBudget(1);
    when(source.discover(FIRST, 100)).thenReturn(List.of(a));
    when(source.discover(a, 100)).thenReturn(List.of());
    when(service.repair(a)).thenReturn(true);
    when(service.openObligations(FIRST, 100)).thenReturn(List.of(b));
    when(source.closed(List.of(b))).thenReturn(Set.of(b));

    job.scanTenant();

    verify(service, never()).repair(b);
    verify(health).finish(tenant, true);
  }

  @Test
  void aFailingDiscoveryIsRecordedAsStaleAndDoesNotKeepTheOtherPhaseFromRunning() {
    when(source.discover(FIRST, 100)).thenThrow(new IllegalStateException("down"));
    when(service.openObligations(FIRST, 100)).thenReturn(List.of(a));
    when(source.closed(List.of(a))).thenReturn(Set.of(a));
    when(service.openObligations(a, 100)).thenReturn(List.of());

    job.scanTenant();

    verify(service).repair(a);
    verify(health).finish(tenant, true);
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
  void theScheduledScanOnlyHandsEachTenantOverAndSkipsOneStillRunning() {
    UUID running = UUID.randomUUID(), free = UUID.randomUUID();
    when(tenants.findAllActiveTenants())
        .thenReturn(List.of(tenantNamed(running), tenantNamed(free)));
    when(executor.submit(eq(running), any())).thenReturn(false);
    when(executor.submit(eq(free), any())).thenReturn(true);

    job.scan();

    verify(executor).submit(eq(running), any());
    verify(executor).submit(eq(free), any());
    // Handing over reads nothing and holds no lease: that happens on the worker.
    verifyNoInteractions(source, service, lease, health);
  }

  @Test
  void aHandedOverTenantIsScannedInItsOwnContextWhileHoldingItsLease() throws Exception {
    UUID id = UUID.randomUUID();
    Connection connection = mock(Connection.class);
    when(tenants.findAllActiveTenants()).thenReturn(List.of(tenantNamed(id)));
    when(lease.tryAcquire(id)).thenReturn(Optional.of(new Hold(connection)));
    when(source.discover(FIRST, 100)).thenReturn(List.of());
    when(service.openObligations(FIRST, 100)).thenReturn(List.of());
    executorRunsInline();

    job.scan();

    verify(source).discover(FIRST, 100);
    verify(health).finish(id, false);
    // The lease is let go once the scan is over, which ends its transaction.
    verify(connection).rollback();
    verify(connection).close();
  }

  @Test
  void aTenantWhoseLeaseIsHeldElsewhereIsNotScannedHere() {
    UUID id = UUID.randomUUID();
    when(tenants.findAllActiveTenants()).thenReturn(List.of(tenantNamed(id)));
    when(lease.tryAcquire(id)).thenReturn(Optional.empty());
    executorRunsInline();

    job.scan();

    verifyNoInteractions(source, service, health);
  }

  @Test
  void theLeaseIsReleasedAndTheOtherTenantsStillRunWhenOneTenantsScanBlowsUp() throws Exception {
    UUID broken = UUID.randomUUID(), fine = UUID.randomUUID();
    Connection brokenConnection = mock(Connection.class);
    Connection fineConnection = mock(Connection.class);
    when(tenants.findAllActiveTenants())
        .thenReturn(List.of(tenantNamed(broken), tenantNamed(fine)));
    when(lease.tryAcquire(broken)).thenReturn(Optional.of(new Hold(brokenConnection)));
    when(lease.tryAcquire(fine)).thenReturn(Optional.of(new Hold(fineConnection)));
    doThrow(new IllegalStateException("down")).when(health).finish(eq(broken), anyBoolean());
    when(source.discover(FIRST, 100)).thenReturn(List.of());
    when(service.openObligations(FIRST, 100)).thenReturn(List.of());
    executorRunsInline();

    job.scan();

    verify(health).finish(fine, false);
    verify(brokenConnection).rollback();
    verify(brokenConnection).close();
    verify(fineConnection).rollback();
  }
}
