package com.fabricmanagement.flowboard.generator.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/** StockControlEngine'in IWM StockLedger'e bağımsız erişim noktası (STK1). */
public interface StockQueryPort {

  /** Belirtilen sipariş için sistemdeki (IWM) anlık stok miktarını döndürür. */
  BigDecimal getAvailableStockForOrder(UUID tenantId, UUID salesOrderId);
}
