package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentProgressService {

  private final SalesOrderLineRepository salesOrderLineRepository;
  private final SalesOrderRepository salesOrderRepository;

  /**
   * Faz 1 — Satır shippedQty güncelleme. Nadiren çakışır (satır-özel), hızla commit eder.
   * İdempotent: processedShipmentLineIds guard'ı sayesinde tekrar event gelirse skip.
   *
   * @return güncellenen satırın salesOrderId'si, satır bulunamazsa null
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID recordLineShipment(
      UUID salesOrderLineId, UUID shipmentLineId, BigDecimal confirmedQuantity) {
    UUID tenantId =
        com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId();
    SalesOrderLine line =
        salesOrderLineRepository.findByTenantIdAndId(tenantId, salesOrderLineId).orElse(null);
    if (line == null) {
      log.error("SalesOrderLine not found: {}", salesOrderLineId);
      return null;
    }

    boolean shipmentApplied = line.addShippedQuantity(shipmentLineId, confirmedQuantity);
    salesOrderLineRepository.save(line);

    if (shipmentApplied && line.isOverShipped()) {
      log.warn(
          "Over-shipment detected for SalesOrderLine {}. shipmentLineId={}, requestedQty={}, shippedQty={}, remainingQty={}",
          line.getId(),
          shipmentLineId,
          line.getRequestedQty(),
          line.getShippedQty(),
          line.getRemainingQty());
    }

    log.info(
        "Updated shipped quantity for SalesOrderLine {}. shippedQty={}",
        line.getId(),
        line.getShippedQty());
    return line.getSalesOrderId();
  }

  /**
   * Faz 2 — Header status aggregate ve güncelleme. Ayrı REQUIRES_NEW tx — optimistic lock çakışması
   * burada olur. Retry'da tüm veri taze okunur, header status yeniden hesaplanır → idempotent.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateOrderShipmentStatus(UUID salesOrderId) {
    // 1. Tüm aktif, CANCELLED olmayan satırları çek
    List<SalesOrderLine> activeLines =
        salesOrderLineRepository
            .findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(salesOrderId)
            .stream()
            .filter(l -> l.getLineStatus() != SalesOrderLineStatus.CANCELLED)
            .toList();

    // 2. Sevk durumunu hesapla
    boolean anyLineShipped =
        activeLines.stream()
            .anyMatch(
                l -> l.getShippedQty() != null && l.getShippedQty().compareTo(BigDecimal.ZERO) > 0);

    boolean allLinesFullyShipped =
        !activeLines.isEmpty()
            && activeLines.stream()
                .allMatch(
                    l ->
                        l.getRequestedQty() != null
                            && l.getShippedQty() != null
                            && l.getShippedQty().compareTo(l.getRequestedQty()) >= 0);

    // 3. Header status güncelle
    UUID tenantId =
        com.fabricmanagement.common.infrastructure.persistence.TenantContext.getCurrentTenantId();
    SalesOrder order =
        salesOrderRepository.findByTenantIdAndId(tenantId, salesOrderId).orElse(null);
    if (order == null) {
      log.error("SalesOrder not found for shipment progress: {}", salesOrderId);
      return;
    }

    OrderStatus previousStatus = order.getStatus();
    order.recordShipmentProgress(allLinesFullyShipped, anyLineShipped);

    if (previousStatus != order.getStatus()) {
      salesOrderRepository.save(order);
      log.info(
          "Order {} status changed: {} → {}",
          order.getOrderNumber(),
          previousStatus,
          order.getStatus());
    } else {
      log.debug(
          "Order {} status unchanged: {} (anyShipped={}, allShipped={})",
          order.getOrderNumber(),
          previousStatus,
          anyLineShipped,
          allLinesFullyShipped);
    }
  }
}
