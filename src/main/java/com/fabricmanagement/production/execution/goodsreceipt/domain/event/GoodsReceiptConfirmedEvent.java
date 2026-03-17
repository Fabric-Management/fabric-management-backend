package com.fabricmanagement.production.execution.goodsreceipt.domain.event;

import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Domain event published when a GoodsReceipt transitions to CONFIRMED. Used by IWM to process stock
 * movements, and by Procurement to update PO/SC status.
 */
@Builder
public record GoodsReceiptConfirmedEvent(
    UUID tenantId,
    UUID receiptId,
    String receiptNumber,
    GoodsReceiptSourceType sourceType,
    UUID sourceId,
    Instant confirmedAt,
    List<ReceiptItemData> items) {

  @Builder
  public record ReceiptItemData(
      UUID itemId, String barcode, BigDecimal netWeight, BigDecimal grossWeight) {}
}
