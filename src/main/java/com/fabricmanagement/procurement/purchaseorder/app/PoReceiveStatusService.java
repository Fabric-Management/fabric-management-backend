package com.fabricmanagement.procurement.purchaseorder.app;

import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.purchaseorder.app.port.PoGoodsReceiptReadPort;
import com.fabricmanagement.procurement.purchaseorder.app.port.PoGoodsReceiptReadPort.LineReceiptTotal;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderLine;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoPartiallyReceivedEvent;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoReceivedEvent;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderLineRepository;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Derives persisted PO receive status and read-model coverage from confirmed goods receipts. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoReceiveStatusService {

  private static final Set<PurchaseOrderStatus> ELIGIBLE_SOURCE_STATUSES =
      Set.of(PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.PARTIALLY_RECEIVED);

  private final PurchaseOrderRepository purchaseOrderRepository;
  private final PurchaseOrderLineRepository purchaseOrderLineRepository;
  private final PoGoodsReceiptReadPort goodsReceiptReadPort;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Fully re-derives receive status. No receipt event is incrementally applied, so redelivery
   * converges on the same result.
   */
  @Transactional
  public void recomputeReceiveStatus(UUID tenantId, UUID purchaseOrderId) {
    PurchaseOrder purchaseOrder =
        purchaseOrderRepository
            .findByIdAndTenantIdAndIsActiveTrue(purchaseOrderId, tenantId)
            .orElseThrow(
                () ->
                    new ProcurementDomainException(
                        "PurchaseOrder not found with id: " + purchaseOrderId));

    if (!ELIGIBLE_SOURCE_STATUSES.contains(purchaseOrder.getStatus())) {
      log.warn(
          "Skipping PO receive-status derivation for ineligible status: tenant={}, po={}, status={}",
          tenantId,
          purchaseOrderId,
          purchaseOrder.getStatus());
      return;
    }

    List<PurchaseOrderLine> lines =
        purchaseOrderLineRepository
            .findByTenantIdAndPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
                tenantId, purchaseOrderId);
    if (lines.isEmpty()) {
      log.warn(
          "Skipping PO receive-status derivation because no active lines exist: tenant={}, po={}",
          tenantId,
          purchaseOrderId);
      return;
    }

    CoverageSnapshot snapshot = coverageSnapshot(tenantId, purchaseOrderId, lines);
    if (!snapshot.hasConfirmedReceipts()) {
      return;
    }

    int completeLineCount =
        Math.toIntExact(
            lines.stream()
                .filter(
                    line ->
                        snapshot
                                .coverageByLine()
                                .getOrDefault(line.getId(), LineCoverage.empty())
                                .receivedQty()
                                .compareTo(line.getQty())
                            >= 0)
                .count());
    PurchaseOrderStatus targetStatus =
        completeLineCount == lines.size()
            ? PurchaseOrderStatus.RECEIVED
            : PurchaseOrderStatus.PARTIALLY_RECEIVED;

    if (purchaseOrder.getStatus() == targetStatus) {
      return;
    }
    if (!purchaseOrder.getStatus().canTransitionTo(targetStatus)) {
      log.warn(
          "Skipping invalid derived PO transition: tenant={}, po={}, current={}, target={}",
          tenantId,
          purchaseOrderId,
          purchaseOrder.getStatus(),
          targetStatus);
      return;
    }

    purchaseOrder.setStatus(targetStatus);
    purchaseOrderRepository.saveAndFlush(purchaseOrder);

    if (targetStatus == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
      eventPublisher.publishEvent(
          new PoPartiallyReceivedEvent(
              tenantId,
              purchaseOrderId,
              purchaseOrder.getPoNumber(),
              completeLineCount,
              lines.size()));
    } else {
      eventPublisher.publishEvent(
          new PoReceivedEvent(
              tenantId,
              purchaseOrderId,
              purchaseOrder.getPoNumber(),
              completeLineCount,
              lines.size()));
    }
  }

  @Transactional(readOnly = true)
  public Map<UUID, LineCoverage> getLineCoverage(
      UUID tenantId, UUID purchaseOrderId, List<PurchaseOrderLine> lines) {
    return coverageSnapshot(tenantId, purchaseOrderId, lines).coverageByLine();
  }

  private CoverageSnapshot coverageSnapshot(
      UUID tenantId, UUID purchaseOrderId, List<PurchaseOrderLine> lines) {
    Map<UUID, String> lineUnits = new LinkedHashMap<>();
    lines.forEach(line -> lineUnits.put(line.getId(), line.getUnit()));

    var totals = goodsReceiptReadPort.sumReceivedByLine(tenantId, purchaseOrderId, lineUnits);
    Map<UUID, LineCoverage> coverageByLine = new LinkedHashMap<>();
    lines.forEach(
        line -> {
          LineReceiptTotal total =
              totals
                  .receivedByLine()
                  .getOrDefault(line.getId(), new LineReceiptTotal(BigDecimal.ZERO, 0, false));
          coverageByLine.put(
              line.getId(), new LineCoverage(total.receivedQty(), total.receiveMismatch()));
        });
    return new CoverageSnapshot(totals.hasConfirmedReceipts(), Map.copyOf(coverageByLine));
  }

  public record LineCoverage(BigDecimal receivedQty, boolean receiveMismatch) {

    public LineCoverage {
      receivedQty = receivedQty != null ? receivedQty : BigDecimal.ZERO;
    }

    public static LineCoverage empty() {
      return new LineCoverage(BigDecimal.ZERO, false);
    }
  }

  private record CoverageSnapshot(
      boolean hasConfirmedReceipts, Map<UUID, LineCoverage> coverageByLine) {}
}
