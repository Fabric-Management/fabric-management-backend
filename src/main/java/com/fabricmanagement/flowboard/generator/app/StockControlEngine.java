package com.fabricmanagement.flowboard.generator.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.generator.domain.port.out.StockQueryPort;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SalesOrderConfirmed event'i için stok analizi yapar ve hangi task'ların oluşturulacağına karar
 * verir.
 *
 * <p>Kararlar:
 *
 * <ul>
 *   <li>Stok yeterli → WAREHOUSE task
 *   <li>Stok yetersiz → PRODUCTION task
 *   <li>Kısmi stok → ikisi birden (miktarlar bölünür)
 * </ul>
 *
 * <p>Docs: {@code 07-flowboard/smart-task-generator.md} — Bölüm 3. Stok Kontrol Motoru
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StockControlEngine {

  private final StockQueryPort stockPort;

  /** Stok analizi kararı — hangi task tipi, ne kadar miktar, hangi line. */
  public record StockDecision(
      TaskType taskType, BigDecimal quantity, java.util.UUID lineId, java.util.UUID materialId) {}

  /**
   * SalesOrderConfirmedEvent için stok analizi yapar.
   *
   * <p>[STK1 FIX] IWM StockLedger entegrasyonu tamamlandı. StockQueryPort üzerinden gerçek stok
   * sorgusu yapılır. Line-bazlı (materialId) olarak çalışır.
   */
  public List<StockDecision> analyze(SalesOrderConfirmedEvent event) {
    log.info(
        "StockControlEngine.analyze: orderId={} totalQty={}",
        event.getSalesOrderId(),
        event.getTotalQuantity());

    List<StockDecision> decisions = new ArrayList<>();
    java.util.UUID tenantId = TenantContext.getCurrentTenantId();

    for (SalesOrderConfirmedEvent.SalesOrderLineSnapshot line : event.getLines()) {
      if (line.materialId() == null) {
        log.debug("Line {} has no materialId (free-text), deciding PRODUCTION", line.lineId());
        decisions.add(new StockDecision(TaskType.PRODUCTION, line.quantity(), line.lineId(), null));
        continue;
      }

      BigDecimal available =
          stockPort
              .getAvailableStockByMaterial(tenantId, line.materialId())
              .max(BigDecimal.ZERO); // Negatif stok koruması

      log.debug(
          "Line {} (material={}) requested={}, available={}",
          line.lineId(),
          line.materialId(),
          line.quantity(),
          available);

      if (available.compareTo(line.quantity()) >= 0) {
        // Tamamen stokta var
        decisions.add(
            new StockDecision(
                TaskType.WAREHOUSE, line.quantity(), line.lineId(), line.materialId()));
      } else if (available.compareTo(BigDecimal.ZERO) == 0) {
        // Hiç stok yok
        decisions.add(
            new StockDecision(
                TaskType.PRODUCTION, line.quantity(), line.lineId(), line.materialId()));
      } else {
        // Kısmi stok var
        decisions.add(
            new StockDecision(TaskType.WAREHOUSE, available, line.lineId(), line.materialId()));
        decisions.add(
            new StockDecision(
                TaskType.PRODUCTION,
                line.quantity().subtract(available),
                line.lineId(),
                line.materialId()));
      }
    }

    log.info(
        "Stock decisions for order {}: {} task(s) generated from {} lines",
        event.getSalesOrderId(),
        decisions.size(),
        event.getLines().size());

    return decisions;
  }
}
