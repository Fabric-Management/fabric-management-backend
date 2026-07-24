package com.fabricmanagement.procurement.purchaseorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.procurement.purchaseorder.app.port.PoGoodsReceiptReadPort;
import com.fabricmanagement.procurement.purchaseorder.app.port.PoGoodsReceiptReadPort.LineReceiptTotal;
import com.fabricmanagement.procurement.purchaseorder.app.port.PoGoodsReceiptReadPort.PoReceiptTotals;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderLine;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoPartiallyReceivedEvent;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoReceivedEvent;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderLineRepository;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PoReceiveStatusServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PO_ID = UUID.randomUUID();
  private static final UUID LINE_ONE_ID = UUID.randomUUID();
  private static final UUID LINE_TWO_ID = UUID.randomUUID();

  @Mock private PurchaseOrderRepository purchaseOrderRepository;
  @Mock private PurchaseOrderLineRepository lineRepository;
  @Mock private PoGoodsReceiptReadPort receiptReadPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private PoReceiveStatusService service;
  private PurchaseOrder purchaseOrder;
  private PurchaseOrderLine lineOne;

  @BeforeEach
  void setUp() {
    service =
        new PoReceiveStatusService(
            purchaseOrderRepository, lineRepository, receiptReadPort, eventPublisher);
    purchaseOrder = purchaseOrder(PurchaseOrderStatus.CONFIRMED);
    lineOne = line(LINE_ONE_ID, "100", "KG");
    when(purchaseOrderRepository.findByIdAndTenantIdAndIsActiveTrue(PO_ID, TENANT_ID))
        .thenReturn(Optional.of(purchaseOrder));
    when(lineRepository.findByTenantIdAndPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
            TENANT_ID, PO_ID))
        .thenReturn(List.of(lineOne));
  }

  @Test
  void shortReceiptTransitionsToPartiallyReceivedAndPublishesCompletedLineCounts() {
    stubTotals(true, Map.of(LINE_ONE_ID, new LineReceiptTotal(new BigDecimal("40"), 0, true)));

    service.recomputeReceiveStatus(TENANT_ID, PO_ID);

    assertThat(purchaseOrder.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    verify(purchaseOrderRepository).saveAndFlush(purchaseOrder);
    ArgumentCaptor<PoPartiallyReceivedEvent> eventCaptor =
        ArgumentCaptor.forClass(PoPartiallyReceivedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getReceivedItemCount()).isZero();
    assertThat(eventCaptor.getValue().getTotalItemCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"100", "120"})
  void exactAndOverReceiptTransitionDirectlyToReceived(String receivedQty) {
    stubTotals(
        true, Map.of(LINE_ONE_ID, new LineReceiptTotal(new BigDecimal(receivedQty), 0, true)));

    service.recomputeReceiveStatus(TENANT_ID, PO_ID);

    assertThat(purchaseOrder.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
    verify(eventPublisher).publishEvent(any(PoReceivedEvent.class));
  }

  @Test
  void partialPurchaseOrderTransitionsToReceivedAfterFreshFullDerivation() {
    purchaseOrder.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    stubTotals(true, Map.of(LINE_ONE_ID, new LineReceiptTotal(new BigDecimal("100"), 0, true)));

    service.recomputeReceiveStatus(TENANT_ID, PO_ID);

    assertThat(purchaseOrder.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
    verify(purchaseOrderRepository).saveAndFlush(purchaseOrder);
    verify(eventPublisher).publishEvent(any(PoReceivedEvent.class));
  }

  @Test
  void mixedLinesRemainPartialUntilEveryLineIsComplete() {
    PurchaseOrderLine lineTwo = line(LINE_TWO_ID, "50", "M");
    when(lineRepository.findByTenantIdAndPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
            TENANT_ID, PO_ID))
        .thenReturn(List.of(lineOne, lineTwo));
    stubTotals(
        true,
        Map.of(
            LINE_ONE_ID, new LineReceiptTotal(new BigDecimal("110"), 0, true),
            LINE_TWO_ID, new LineReceiptTotal(new BigDecimal("20"), 0, true)));

    service.recomputeReceiveStatus(TENANT_ID, PO_ID);

    assertThat(purchaseOrder.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    ArgumentCaptor<PoPartiallyReceivedEvent> eventCaptor =
        ArgumentCaptor.forClass(PoPartiallyReceivedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getReceivedItemCount()).isEqualTo(1);
    assertThat(eventCaptor.getValue().getTotalItemCount()).isEqualTo(2);
  }

  @Test
  void mismatchCoverageRemainsZeroAndKeepsStatusTruthful() {
    stubTotals(true, Map.of(LINE_ONE_ID, new LineReceiptTotal(BigDecimal.ZERO, 2, false)));

    service.recomputeReceiveStatus(TENANT_ID, PO_ID);
    var coverage = service.getLineCoverage(TENANT_ID, PO_ID, List.of(lineOne)).get(LINE_ONE_ID);

    assertThat(purchaseOrder.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    assertThat(coverage.receivedQty()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(coverage.receiveMismatch()).isTrue();
  }

  @Test
  void rerunInSameDerivedStateDoesNotDuplicateTransitionOrEvent() {
    stubTotals(true, Map.of(LINE_ONE_ID, new LineReceiptTotal(new BigDecimal("40"), 0, true)));

    service.recomputeReceiveStatus(TENANT_ID, PO_ID);
    service.recomputeReceiveStatus(TENANT_ID, PO_ID);

    verify(purchaseOrderRepository, times(1)).saveAndFlush(purchaseOrder);
    verify(eventPublisher, times(1)).publishEvent(any(PoPartiallyReceivedEvent.class));
  }

  @Test
  void noConfirmedReceiptsOrIneligibleStatusCausesNoTransition() {
    stubTotals(false, Map.of(LINE_ONE_ID, new LineReceiptTotal(BigDecimal.ZERO, 0, true)));
    service.recomputeReceiveStatus(TENANT_ID, PO_ID);

    purchaseOrder.setStatus(PurchaseOrderStatus.CANCELLED);
    service.recomputeReceiveStatus(TENANT_ID, PO_ID);

    verify(purchaseOrderRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  private void stubTotals(boolean hasReceipts, Map<UUID, LineReceiptTotal> totals) {
    when(receiptReadPort.sumReceivedByLine(eq(TENANT_ID), eq(PO_ID), any()))
        .thenReturn(new PoReceiptTotals(hasReceipts, totals));
  }

  private PurchaseOrder purchaseOrder(PurchaseOrderStatus status) {
    PurchaseOrder po =
        PurchaseOrder.builder()
            .poNumber("PO-20260723-00001")
            .workOrderId(UUID.randomUUID())
            .tradingPartnerId(UUID.randomUUID())
            .status(status)
            .totalAmount(Money.of(BigDecimal.TEN, "GBP"))
            .build();
    po.setId(PO_ID);
    po.setTenantId(TENANT_ID);
    return po;
  }

  private PurchaseOrderLine line(UUID id, String qty, String unit) {
    PurchaseOrderLine line =
        PurchaseOrderLine.create(
            PO_ID,
            null,
            UUID.randomUUID(),
            null,
            new BigDecimal(qty),
            unit,
            Money.of(BigDecimal.ONE, "GBP"),
            null);
    line.setId(id);
    line.setTenantId(TENANT_ID);
    return line;
  }
}
