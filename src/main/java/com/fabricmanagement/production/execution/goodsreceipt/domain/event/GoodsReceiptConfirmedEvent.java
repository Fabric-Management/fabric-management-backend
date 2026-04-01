package com.fabricmanagement.production.execution.goodsreceipt.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Domain event published when a GoodsReceipt transitions to CONFIRMED. Used by IWM to process stock
 * movements, and by Procurement to update PO/SC status.
 */
@Getter
public class GoodsReceiptConfirmedEvent extends DomainEvent {

  private final UUID receiptId;
  private final String receiptNumber;
  private final GoodsReceiptSourceType sourceType;
  private final UUID sourceId;
  private final List<ReceiptItemData> items;

  @Builder
  public GoodsReceiptConfirmedEvent(
      UUID tenantId,
      UUID receiptId,
      String receiptNumber,
      GoodsReceiptSourceType sourceType,
      UUID sourceId,
      Instant confirmedAt,
      List<ReceiptItemData> items) {
    super(tenantId, "GOODS_RECEIPT_CONFIRMED");
    this.receiptId = receiptId;
    this.receiptNumber = receiptNumber;
    this.sourceType = sourceType;
    this.sourceId = sourceId;
    this.items = items;
  }

  @Builder
  public record ReceiptItemData(
      UUID itemId, String barcode, BigDecimal netWeight, BigDecimal grossWeight) {}
}
