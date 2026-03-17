package com.fabricmanagement.production.execution.goodsreceipt.dto;

import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/** Read-only response for a GoodsReceipt with its line items. */
@Value
@Builder
public class GoodsReceiptResponse {

  UUID id;
  String uid;
  String receiptNumber;
  GoodsReceiptSourceType sourceType;
  UUID sourceId;
  UUID receivedById;
  Instant receivedAt;
  Integer packageCount;
  BigDecimal grossWeight;
  BigDecimal netWeight;
  String vehicleInfo;
  String damageNotes;
  GoodsReceiptStatus status;
  List<GoodsReceiptItemResponse> items;

  @Value
  @Builder
  public static class GoodsReceiptItemResponse {
    UUID id;
    Integer sequenceNo;
    String barcode;
    String serialNumber;
    BigDecimal netWeight;
    BigDecimal grossWeight;
    String notes;
  }
}
