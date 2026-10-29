package com.fabricmanagement.finance.collection.app.port;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * What finance has recorded as received against an order's collection obligations. Finance owns the
 * answer and publishes only facts; the caller decides what they mean for its own workflow. Nothing
 * here locks or changes anything.
 */
public interface CollectionSettlementPort {
  /**
   * Cash currently matched to each obligation, keyed by the obligation identity the payment plan
   * line carries. Matches that were reversed and payments that were voided are not counted.
   * Obligations with nothing recorded are absent, so an empty map means nothing was collected.
   */
  Map<UUID, BigDecimal> settledByObligation(UUID tenantId, UUID orderId);
}
