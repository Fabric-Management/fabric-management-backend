package com.fabricmanagement.production.core.stockunit.api;

import java.util.Collection;
import java.util.UUID;

public interface StockUnitSoftHoldPort {

  void replaceHolds(UUID quoteLineId, Collection<UUID> stockUnitIds);

  void releaseHolds(UUID quoteLineId);

  java.util.List<UUID> heldPieceIds(UUID ownerLineId);

  void transferQuoteHolds(
      UUID quoteId, UUID quoteLineId, UUID orderId, String orderNumber, UUID orderLineId);

  void replaceOrderHolds(
      UUID orderId,
      String orderNumber,
      UUID lineId,
      UUID actor,
      UUID ownSessionId,
      Collection<UUID> pieceIds,
      Collection<OrderLot> lots);

  record OrderLot(UUID batchId, java.math.BigDecimal quantity, String unit) {}
}
