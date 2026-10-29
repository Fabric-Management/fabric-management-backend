package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.*;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentPlanStoreTest {
  @Mock OrderPaymentPlanRepository plans;
  @Mock OrderPaymentPlanRevisionRepository revisions;
  @Mock OrderPaymentPlanCurrencyRepository currencies;
  @Mock OrderPaymentPlanLineRepository lines;
  @Mock SalesOrderLineRepository orderLines;
  @InjectMocks PaymentPlanStore store;
  UUID tenant = UUID.randomUUID(), orderId = UUID.randomUUID();

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void fulfilmentDoesNotInvalidateAgreedCommercialPricingButCancellationDoes() {
    var order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderDate(LocalDate.parse("2026-10-20"))
            .build();
    order.setId(orderId);
    var line =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("100"))
            .unitPriceAmount(new BigDecimal("12.00"))
            .currency("GBP")
            .lineStatus(SalesOrderLineStatus.PENDING)
            .build();
    line.setId(UUID.randomUUID());
    when(orderLines.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
            tenant, orderId))
        .thenReturn(List.of(line));
    var fingerprint = store.fingerprint(order);
    line.setLineStatus(SalesOrderLineStatus.SHIPPED);
    assertThat(store.fingerprint(order)).isEqualTo(fingerprint);
    line.setLineStatus(SalesOrderLineStatus.CANCELLED);
    assertThat(store.fingerprint(order)).isNotEqualTo(fingerprint);
  }

  @Test
  void historyPageLoadsAllCurrencyRowsInTwoQueries() {
    var a =
        OrderPaymentPlanRevision.create(
            UUID.randomUUID(),
            1,
            0L,
            "fingerprint",
            UUID.randomUUID(),
            "hash",
            UUID.randomUUID(),
            "Initial terms",
            List.of());
    a.setId(UUID.randomUUID());
    var b =
        OrderPaymentPlanRevision.create(
            a.getPlanId(),
            2,
            0L,
            "fingerprint",
            UUID.randomUUID(),
            "hash",
            UUID.randomUUID(),
            "Revised terms",
            List.of());
    b.setId(UUID.randomUUID());
    var ga =
        OrderPaymentPlanCurrency.create(
            a.getId(),
            new PaymentPlanContent.CurrencyGroup("GBP", new BigDecimal("4284"), null, List.of()));
    ga.setId(UUID.randomUUID());
    var gb =
        OrderPaymentPlanCurrency.create(
            b.getId(),
            new PaymentPlanContent.CurrencyGroup("GBP", new BigDecimal("4284"), null, List.of()));
    gb.setId(UUID.randomUUID());
    when(currencies.findByTenantIdAndRevisionIdInOrderByCurrencyAsc(
            tenant, List.of(a.getId(), b.getId())))
        .thenReturn(List.of(ga, gb));
    when(lines.findByTenantIdAndCurrencyGroupIdInOrderByOrdinalAsc(
            tenant, List.of(ga.getId(), gb.getId())))
        .thenReturn(List.of());
    assertThat(store.contents(List.of(a, b))).hasSize(2);
    verify(currencies, times(1)).findByTenantIdAndRevisionIdInOrderByCurrencyAsc(any(), any());
    verify(lines, times(1)).findByTenantIdAndCurrencyGroupIdInOrderByOrdinalAsc(any(), any());
    verifyNoMoreInteractions(currencies, lines);
  }

  @Test
  void aPointerToAMissingRevisionIsAServerInvariantFailureNeverAConflictTheClientCouldRetry() {
    var plan = mock(OrderPaymentPlan.class);
    var revisionId = UUID.randomUUID();
    when(revisions.findByTenantIdAndId(tenant, revisionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> store.revision(plan, revisionId))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            failure -> {
              assertThat(failure.getHttpStatus()).isEqualTo(500);
              assertThat(failure.getErrorCode()).isEqualTo("PAYMENT_PLAN_REVISION_MISSING");
            });
  }

  @Test
  void aRevisionBelongingToAnotherPlanIsTreatedAsMissing() {
    var plan = mock(OrderPaymentPlan.class);
    var foreign = mock(OrderPaymentPlanRevision.class);
    var revisionId = UUID.randomUUID();
    when(plan.getId()).thenReturn(UUID.randomUUID());
    when(foreign.getPlanId()).thenReturn(UUID.randomUUID());
    when(revisions.findByTenantIdAndId(tenant, revisionId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> store.revision(plan, revisionId))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            failure -> assertThat(failure.getHttpStatus()).isEqualTo(500));
  }
}
