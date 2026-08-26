package com.fabricmanagement.inventory.reservation.app;

import com.fabricmanagement.inventory.reservation.dto.LotSuggestion;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface StockReservationEngine {
  /** FIFO tabanlı, istenilen miktar için stok önerisinde bulunur. */
  List<LotSuggestion> suggestLotsFifo(UUID tenantId, UUID productId, BigDecimal requiredQty);
}
