package com.fabricmanagement.sales.salesorder.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort.SourceBusyException;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort.SourceStamp;
import com.fabricmanagement.sales.salesorder.app.PaymentPlanStore;
import com.fabricmanagement.sales.salesorder.app.port.AcceptedPaymentPlanQueryPort;
import com.fabricmanagement.sales.salesorder.app.port.AcceptedPaymentPlanQueryPort.AcceptedPlan;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlan;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanRevision;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Lock;

class CollectionSourceAdapterTest {
  final UUID tenant = UUID.randomUUID(), order = UUID.randomUUID();
  AcceptedPaymentPlanQueryPort accepted;
  OrderPaymentPlanRepository plans;
  PaymentPlanStore store;
  SalesOrderRepository orders;
  EntityManager entityManager;
  Query read, write;
  CollectionSourceAdapter adapter;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    accepted = mock(AcceptedPaymentPlanQueryPort.class);
    plans = mock(OrderPaymentPlanRepository.class);
    store = mock(PaymentPlanStore.class);
    orders = mock(SalesOrderRepository.class);
    entityManager = mock(EntityManager.class);
    read = mock(Query.class);
    write = mock(Query.class);
    when(entityManager.createNativeQuery("select current_setting('lock_timeout')"))
        .thenReturn(read);
    when(read.getSingleResult()).thenReturn("0");
    when(entityManager.createNativeQuery("select set_config('lock_timeout', :value, true)"))
        .thenReturn(write);
    when(write.setParameter(anyString(), any())).thenReturn(write);
    adapter = new CollectionSourceAdapter(accepted, plans, orders, store, entityManager);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  @Test
  void financeReadsTheOrderUnderAShareLockAndNeverTheWritersExclusiveLock() {
    when(orders.lockSharedByTenantIdAndId(tenant, order)).thenReturn(Optional.empty());
    assertThat(adapter.find(tenant, order)).isEmpty();
    verify(orders).lockSharedByTenantIdAndId(tenant, order);
    verify(orders, never()).lockByTenantIdAndId(any(), any());
    verifyNoInteractions(accepted);
  }

  @Test
  void aWriterIsAwaitedBrieflyAndTheCallersOwnTimeoutComesBack() {
    when(orders.lockSharedByTenantIdAndId(tenant, order))
        .thenReturn(Optional.of(mock(SalesOrder.class)));
    when(accepted.find(tenant, order)).thenReturn(Optional.empty());
    assertThat(adapter.find(tenant, order)).isEmpty();
    var steps = inOrder(write, orders, accepted);
    steps.verify(write).setParameter("value", CollectionSourceAdapter.LOCK_WAIT);
    steps.verify(orders).lockSharedByTenantIdAndId(tenant, order);
    steps.verify(write).setParameter("value", "0");
    steps.verify(accepted).find(tenant, order);
  }

  @Test
  void aWriterThatHoldsTheOrderTooLongMakesTheOrderBusyInsteadOfStallingFinance() {
    when(orders.lockSharedByTenantIdAndId(tenant, order))
        .thenThrow(new CannotAcquireLockException("lock timeout"));
    assertThatThrownBy(() -> adapter.find(tenant, order))
        .isInstanceOf(SourceBusyException.class)
        .hasMessageContaining(order.toString());
    // The transaction is aborted, so nothing more is attempted on it: no restore, no plan read.
    verify(write, times(1)).setParameter(anyString(), any());
    verifyNoInteractions(accepted);
  }

  @Test
  void askingForAnotherTenantsOrderIsABugThatIsNeitherAnsweredNorReadAsNoOrder() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    assertThatThrownBy(() -> adapter.find(tenant, order))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("outside the bound tenant");
    verifyNoInteractions(orders, accepted, entityManager);
  }

  private Query rows(String sql, java.util.List<Object> found) {
    var query = mock(Query.class);
    when(entityManager.createNativeQuery(sql)).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultList()).thenReturn(found);
    return query;
  }

  @Test
  void anOrderNobodyHoldsIsShareLockedWithoutWaitingAndWithoutTouchingTheTimeout() {
    rows(CollectionSourceAdapter.EXISTS_SQL, java.util.List.of(1));
    rows(CollectionSourceAdapter.HOLD_NOW_SQL, java.util.List.of(1));
    assertThat(adapter.tryHold(tenant, order)).isTrue();
    verifyNoInteractions(orders, accepted, write);
  }

  @Test
  void anOrderAWriterHoldsIsReportedBusyInsteadOfWaitedFor() {
    rows(CollectionSourceAdapter.EXISTS_SQL, java.util.List.of(1));
    rows(CollectionSourceAdapter.HOLD_NOW_SQL, java.util.List.of());
    assertThat(adapter.tryHold(tenant, order)).isFalse();
    verifyNoInteractions(orders, accepted, write);
  }

  @Test
  void anOrderThatDoesNotExistIsNotBusyAndNothingIsLocked() {
    rows(CollectionSourceAdapter.EXISTS_SQL, java.util.List.of());
    assertThat(adapter.tryHold(tenant, order)).isTrue();
    verify(entityManager, never()).createNativeQuery(CollectionSourceAdapter.HOLD_NOW_SQL);
  }

  @Test
  void theNonWaitingLockSkipsLockedRowsAndNeverBlocks() {
    assertThat(CollectionSourceAdapter.HOLD_NOW_SQL)
        .endsWith("for share skip locked")
        .contains("tenant_id = :tenant");
  }

  @Test
  void anotherTenantsOrderIsNeverProbedAndNeverReportedAsNothingToLock() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    assertThatThrownBy(() -> adapter.tryHold(tenant, order))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(orders, accepted);
    verify(entityManager, never()).createNativeQuery(CollectionSourceAdapter.EXISTS_SQL);
  }

  @Test
  void financeShareLocksWhileEveryWriterOfTheOrderKeepsTheExclusiveLock() throws Exception {
    var shared =
        SalesOrderRepository.class.getMethod("lockSharedByTenantIdAndId", UUID.class, UUID.class);
    var exclusive =
        SalesOrderRepository.class.getMethod("lockByTenantIdAndId", UUID.class, UUID.class);
    assertThat(shared.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_READ);
    assertThat(exclusive.getAnnotation(Lock.class).value())
        .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }

  private OrderPaymentPlanRepository.AgreedStamp listed(
      UUID forOrder, long orderVersion, long planVersion, UUID revision, boolean cancelled) {
    var value = mock(OrderPaymentPlanRepository.AgreedStamp.class);
    when(value.getOrderId()).thenReturn(forOrder);
    when(value.getOrderVersion()).thenReturn(orderVersion);
    when(value.getPlanVersion()).thenReturn(planVersion);
    when(value.getRevisionId()).thenReturn(revision);
    when(value.getCancelled()).thenReturn(cancelled);
    return value;
  }

  @Test
  void stampsListAgreedOrdersByVersionWithoutLoadingOrLockingAnything() {
    UUID after = new UUID(0, 0), revision = UUID.randomUUID();
    var stamp = listed(order, 9, 4, revision, true);
    when(plans.agreedStampsAfter(tenant, after, PageRequest.of(0, 200))).thenReturn(List.of(stamp));
    assertThat(adapter.stamps(tenant, after, 500))
        .containsExactly(new SourceStamp(order, 9, 4, revision, true));
    verifyNoInteractions(accepted, orders, store, entityManager);
  }

  @Test
  void aSmallerPageThanTheCapIsHonoured() {
    UUID after = new UUID(0, 0);
    when(plans.agreedStampsAfter(tenant, after, PageRequest.of(0, 50))).thenReturn(List.of());
    assertThat(adapter.stamps(tenant, after, 50)).isEmpty();
    verify(plans).agreedStampsAfter(tenant, after, PageRequest.of(0, 50));
  }

  @Test
  void anotherTenantsOrdersAreNeverListedAndNeverReportedAsAnEmptyRun() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    assertThatThrownBy(() -> adapter.stamps(tenant, new UUID(0, 0), 200))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(plans);
  }

  @Test
  void theAgreementCarriesTheOrderVersionItWasReadAtSoTheStampComparisonStaysExact() {
    UUID partner = UUID.randomUUID(), planId = UUID.randomUUID(), revisionId = UUID.randomUUID();
    UUID approval = UUID.randomUUID();
    Instant at = Instant.parse("2026-10-23T08:00:00Z");
    var salesOrder = mock(SalesOrder.class);
    when(salesOrder.getVersion()).thenReturn(9L);
    when(orders.lockSharedByTenantIdAndId(tenant, order)).thenReturn(Optional.of(salesOrder));
    when(orders.findByTenantIdAndId(tenant, order)).thenReturn(Optional.of(salesOrder));
    var content = new PaymentPlanContent(revisionId, 1, "fingerprint", List.of(), List.of());
    var plan = new AcceptedPlan(order, partner, planId, 4L, content, approval, at, at, null, false);
    when(accepted.find(tenant, order)).thenReturn(Optional.of(plan));
    var root = mock(OrderPaymentPlan.class);
    when(root.getActiveAgreedRevisionId()).thenReturn(revisionId);
    var revision = mock(OrderPaymentPlanRevision.class);
    when(store.plan(order)).thenReturn(Optional.of(root));
    when(store.revision(root, revisionId)).thenReturn(revision);
    when(store.stale(salesOrder, revision)).thenReturn(true);
    var agreement = adapter.find(tenant, order).orElseThrow();
    assertThat(agreement.orderVersion()).isEqualTo(9L);
    assertThat(agreement.planVersion()).isEqualTo(4L);
    assertThat(agreement.stale()).isTrue();
  }
}
