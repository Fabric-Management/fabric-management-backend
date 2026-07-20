package com.fabricmanagement.production.execution.goodsreceipt.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
  private final UUID sourceLineId;
  private final String supplierBatchCode;
  private final List<ReceiptItemData> items;

  @Builder
  public GoodsReceiptConfirmedEvent(
      UUID tenantId,
      UUID receiptId,
      String receiptNumber,
      GoodsReceiptSourceType sourceType,
      UUID sourceId,
      UUID sourceLineId,
      String supplierBatchCode,
      Instant confirmedAt,
      List<ReceiptItemData> items) {
    super(tenantId, "GOODS_RECEIPT_CONFIRMED");
    this.receiptId = receiptId;
    this.receiptNumber = receiptNumber;
    this.sourceType = sourceType;
    this.sourceId = sourceId;
    this.sourceLineId = sourceLineId;
    this.supplierBatchCode = supplierBatchCode;
    this.items = items;
  }

  @Builder
  public record ReceiptItemData(
      UUID itemId,
      String barcode,
      BigDecimal netWeight,
      BigDecimal grossWeight,
      BigDecimal length,
      String lengthUnit) {}

  @JsonCreator
  public GoodsReceiptConfirmedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("receiptId") UUID receiptId,
      @JsonProperty("receiptNumber") String receiptNumber,
      @JsonProperty("sourceType") GoodsReceiptSourceType sourceType,
      @JsonProperty("sourceId") UUID sourceId,
      @JsonProperty("sourceLineId") UUID sourceLineId,
      @JsonProperty("supplierBatchCode") String supplierBatchCode,
      @JsonProperty("items") List<ReceiptItemData> items) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "GOODS_RECEIPT_CONFIRMED",
        occurredAt,
        correlationId);
    this.receiptId = receiptId;
    this.receiptNumber = receiptNumber;
    this.sourceType = sourceType;
    this.sourceId = sourceId;
    this.sourceLineId = sourceLineId;
    this.supplierBatchCode = supplierBatchCode;
    this.items = items;
  }
}
