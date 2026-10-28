package com.fabricmanagement.sales.salesorder.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.app.PaymentPlanStore;
import com.fabricmanagement.sales.salesorder.app.port.AcceptedPaymentPlanQueryPort.AcceptedPlan;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlan;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanRevision;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Finance rebuilds its expectations from this answer, so "no accepted plan" must mean exactly that:
 * an unagreed, not yet active or missing plan, never a mistake the caller made.
 */
class AcceptedPaymentPlanQueryAdapterTest {
  final UUID tenant = UUID.randomUUID(), order = UUID.randomUUID();
  final UUID partner = UUID.randomUUID(), planId = UUID.randomUUID();
  final UUID revisionId = UUID.randomUUID(), approval = UUID.randomUUID();
  final Instant agreedAt = Instant.parse("2026-10-20T09:00:00Z");
  final Instant activatedAt = Instant.parse("2026-10-21T09:00:00Z");
  final LocalDate confirmation = LocalDate.of(2026, 10, 21);
  PaymentPlanStore store;
  SalesOrderRepository orders;
  SalesOrder salesOrder;
  OrderPaymentPlan root;
  OrderPaymentPlanRevision revision;
  AcceptedPaymentPlanQueryAdapter adapter;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    store = mock(PaymentPlanStore.class);
    orders = mock(SalesOrderRepository.class);
    salesOrder = mock(SalesOrder.class);
    root = mock(OrderPaymentPlan.class);
    revision = mock(OrderPaymentPlanRevision.class);
    adapter = new AcceptedPaymentPlanQueryAdapter(store, orders);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  private void agreed() {
    when(orders.findByTenantIdAndId(tenant, order)).thenReturn(Optional.of(salesOrder));
    when(salesOrder.getTradingPartnerId()).thenReturn(partner);
    when(store.plan(order)).thenReturn(Optional.of(root));
    when(root.getActiveAgreedRevisionId()).thenReturn(revisionId);
    when(store.revision(root, revisionId)).thenReturn(revision);
  }

  @Test
  void askingForAnotherTenantIsABugAndNeverTheAnswerNoPlan() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    assertThatThrownBy(() -> adapter.find(tenant, order))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("outside the bound tenant");
    verifyNoInteractions(orders, store);
  }

  @Test
  void anOrderThatDoesNotExistHasNoAcceptedPlan() {
    when(orders.findByTenantIdAndId(tenant, order)).thenReturn(Optional.empty());
    assertThat(adapter.find(tenant, order)).isEmpty();
    verifyNoInteractions(store);
  }

  @Test
  void anOrderWithoutAPlanHasNoAcceptedPlan() {
    when(orders.findByTenantIdAndId(tenant, order)).thenReturn(Optional.of(salesOrder));
    when(store.plan(order)).thenReturn(Optional.empty());
    assertThat(adapter.find(tenant, order)).isEmpty();
  }

  @Test
  void aPlanNobodyHasAgreedYetHasNoAcceptedPlanAndLoadsNoRevision() {
    when(orders.findByTenantIdAndId(tenant, order)).thenReturn(Optional.of(salesOrder));
    when(store.plan(order)).thenReturn(Optional.of(root));
    when(root.getActiveAgreedRevisionId()).thenReturn(null);
    assertThat(adapter.find(tenant, order)).isEmpty();
    verify(store, never()).revision(any(), any());
  }

  @Test
  void anAgreedRevisionThatIsNotActiveYetHasNoAcceptedPlanAndReadsNoContent() {
    agreed();
    when(revision.getActivatedAt()).thenReturn(null);
    assertThat(adapter.find(tenant, order)).isEmpty();
    verify(store, never()).content(any());
  }

  @Test
  void anActivatedAgreementIsHandedOverWithEverythingFinanceNeeds() {
    agreed();
    var content = new PaymentPlanContent(revisionId, 1, "fingerprint", List.of(), List.of());
    when(root.getId()).thenReturn(planId);
    when(root.getVersion()).thenReturn(4L);
    when(root.isCancelled()).thenReturn(false);
    when(store.content(revision)).thenReturn(content);
    when(revision.getActivatedAt()).thenReturn(activatedAt);
    when(revision.getAgreedApprovalId()).thenReturn(approval);
    when(revision.getAgreedAt()).thenReturn(agreedAt);
    when(revision.getActivationDate()).thenReturn(confirmation);

    assertThat(adapter.find(tenant, order))
        .contains(
            new AcceptedPlan(
                order,
                partner,
                planId,
                4L,
                content,
                approval,
                agreedAt,
                activatedAt,
                confirmation,
                false));
  }

  @Test
  void aCancelledPlanIsStillReportedSoFinanceCanCancelWhatItExpected() {
    agreed();
    when(root.isCancelled()).thenReturn(true);
    when(store.content(revision))
        .thenReturn(new PaymentPlanContent(revisionId, 1, "fingerprint", List.of(), List.of()));
    when(revision.getActivatedAt()).thenReturn(activatedAt);

    assertThat(adapter.find(tenant, order).orElseThrow().cancelled()).isTrue();
  }
}
