package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.SalesQuantityPolicyOperation;
import com.fabricmanagement.sales.orderintake.infra.repository.SalesQuantityPolicyOperationRepository;
import com.fabricmanagement.sales.quote.dto.QuoteRollSelectionRequests;
import com.fabricmanagement.sales.salesorder.app.SalesOrderLeaseGuard;
import com.fabricmanagement.sales.salesorder.app.SalesOrderRevision;
import com.fabricmanagement.sales.salesorder.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class SalesQuantityPolicyServiceTest {
  final UUID tenant = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      orderId = UUID.randomUUID(),
      lineId = UUID.randomUUID();
  final OrderIntakeAccess access = mock(OrderIntakeAccess.class);
  final SalesOrderRevision revision = mock(SalesOrderRevision.class);
  final SalesOrderLeaseGuard leases = mock(SalesOrderLeaseGuard.class);
  final SalesQuantityPolicyOperationRepository operations =
      mock(SalesQuantityPolicyOperationRepository.class);
  final EntityManager entityManager = mock(EntityManager.class);
  final ProvisionalStockLifecycleService provisional = mock(ProvisionalStockLifecycleService.class);
  final SalesQuantityPolicyService service =
      new SalesQuantityPolicyService(
          access,
          revision,
          leases,
          operations,
          Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC),
          entityManager,
          provisional);
  SalesOrder order;
  SalesOrderLine line;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    order = SalesOrder.builder().orderNumber("SO-2026-00125").build();
    order.setId(orderId);
    order.setVersion(4L);
    line =
        SalesOrderLine.builder()
            .salesOrderId(orderId)
            .productId(UUID.randomUUID())
            .requestedQty(new BigDecimal("2050"))
            .customerRequestQty(new BigDecimal("2050"))
            .unit("M")
            .build();
    line.setId(lineId);
    when(access.writableOrderFresh(orderId, actor)).thenReturn(order);
    when(access.line(order, lineId)).thenReturn(line);
    var query = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    doAnswer(
            invocation -> {
              order.setVersion(5L);
              return null;
            })
        .when(revision)
        .linesChanged(order);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void aTracedExactDecisionReleasesOnlyProvisionalCandidatesAndReplaysItsVersion() {
    var request = request(4L);
    var saved = service.record(orderId, lineId, request, actor);
    var receipt = org.mockito.ArgumentCaptor.forClass(SalesQuantityPolicyOperation.class);
    verify(operations).save(receipt.capture());
    assertThat(saved.orderVersion()).isEqualTo(5L);
    assertThat(saved.quantityTerms().recordedBy()).isEqualTo(actor);
    when(operations.findByTenantIdAndOperationId(tenant, request.operationId()))
        .thenReturn(Optional.of(receipt.getValue()));
    order.setVersion(99L);
    assertThat(service.record(orderId, lineId, request, actor)).isEqualTo(saved);
    verify(provisional, times(1)).ownerLine(lineId);
    verify(revision, times(1)).linesChanged(order);
    verify(leases).assertLineFieldFree(order, lineId, OrderEditKey.LINE_QUANTITY, actor);
  }

  @Test
  void aStaleVersionCannotChangeTheDecisionOrReleaseStock() {
    assertThatThrownBy(() -> service.record(orderId, lineId, request(3L), actor))
        .isInstanceOf(OrderIntakeException.class);
    assertThat(line.getQuantityPolicy()).isEqualTo(SalesQuantityPolicy.UNRECORDED);
    verifyNoInteractions(provisional, leases);
    verify(operations, never()).save(any());
  }

  @Test
  void exactCannotBeRecordedOverAnInflatedCommercialQuantity() {
    line.applyStockQuantity(new BigDecimal("2050"), new BigDecimal("2125"));
    assertThatThrownBy(() -> service.record(orderId, lineId, request(4L), actor))
        .isInstanceOf(OrderIntakeException.class);
    verifyNoInteractions(provisional);
    verify(revision, never()).linesChanged(any());
    verify(operations, never()).save(any());
  }

  @Test
  void anUnknownHistoricalRequestIsNotPromotedToExactConsent() {
    line.inheritQuantityTerms(null, null);
    assertThatThrownBy(() -> service.record(orderId, lineId, request(4L), actor))
        .isInstanceOf(OrderIntakeException.class);
    verifyNoInteractions(provisional);
    verify(operations, never()).save(any());
  }

  @Test
  void anotherActorsOperationCannotBeReplayed() {
    var request = request(4L);
    var terms =
        new com.fabricmanagement.sales.common.dto.SalesQuantityTermsDto(
            SalesQuantityPolicy.EXACT, actor, Instant.now(), request.reason());
    when(operations.findByTenantIdAndOperationId(tenant, request.operationId()))
        .thenReturn(
            Optional.of(
                SalesQuantityPolicyOperation.record(
                    orderId,
                    lineId,
                    UUID.randomUUID(),
                    request.operationId(),
                    4L,
                    5L,
                    request.reason(),
                    terms)));
    assertThatThrownBy(() -> service.record(orderId, lineId, request, actor))
        .isInstanceOf(OrderIntakeException.class);
    verifyNoInteractions(provisional, leases);
  }

  private QuoteRollSelectionRequests.Policy request(long version) {
    return new QuoteRollSelectionRequests.Policy(
        UUID.randomUUID(),
        version,
        SalesQuantityPolicy.EXACT,
        "Customer requires exactly 2,050 metres");
  }
}
