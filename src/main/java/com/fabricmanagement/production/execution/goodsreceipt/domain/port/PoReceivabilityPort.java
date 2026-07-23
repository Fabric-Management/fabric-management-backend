package com.fabricmanagement.production.execution.goodsreceipt.domain.port;

import java.util.UUID;

/** Consumer-owned port for validating whether a purchase order can accept goods receipts. */
public interface PoReceivabilityPort {

  boolean isReceivable(UUID tenantId, UUID purchaseOrderId);
}
