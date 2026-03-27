package com.fabricmanagement.flowboard.generator.domain.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/** Independent access point to the IWM StockLedger for the StockControlEngine (STK1). */
public interface StockQueryPort {

  /** Returns the real-time stock quantity in the system (IWM) for the specified order. */
  BigDecimal getAvailableStockForOrder(UUID tenantId, UUID salesOrderId);
}
