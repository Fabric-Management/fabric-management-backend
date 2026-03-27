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

  /** Stok analizi kararı — hangi task tipi, ne kadar miktar. */
  public record StockDecision(TaskType taskType, BigDecimal quantity) {}

  /**
   * SalesOrderConfirmedEvent için stok analizi yapar.
   *
   * <p>[STK1 FIX] IWM StockLedger entegrasyonu tamamlandı. StockQueryPort üzerinden gerçek stok
   * sorgusu yapılır.
   */
  public List<StockDecision> analyze(SalesOrderConfirmedEvent event) {
    log.info(
        "StockControlEngine.analyze: orderId={} qty={}",
        event.getSalesOrderId(),
        event.getTotalQuantity());

    // [STK1 FIX] Gerçek stok sorgulama IWM modülünden yapılıyor
    BigDecimal available =
        stockPort
            .getAvailableStockForOrder(TenantContext.getCurrentTenantId(), event.getSalesOrderId())
            .max(BigDecimal.ZERO); // [O3 FIX] Negatif stok koruması

    List<StockDecision> decisions = new ArrayList<>();

    if (available.compareTo(event.getTotalQuantity()) >= 0) {
      // Tamamen stokta var
      decisions.add(new StockDecision(TaskType.WAREHOUSE, event.getTotalQuantity()));
    } else if (available.compareTo(BigDecimal.ZERO) == 0) {
      // Hiç stok yok
      decisions.add(new StockDecision(TaskType.PRODUCTION, event.getTotalQuantity()));
    } else {
      // Kısmi stok var
      decisions.add(new StockDecision(TaskType.WAREHOUSE, available));
      decisions.add(
          new StockDecision(TaskType.PRODUCTION, event.getTotalQuantity().subtract(available)));
    }

    return decisions;
  }
}
