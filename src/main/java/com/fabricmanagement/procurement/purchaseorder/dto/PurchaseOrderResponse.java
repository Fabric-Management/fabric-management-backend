package com.fabricmanagement.procurement.purchaseorder.dto;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PurchaseOrderResponse {

  UUID id;
  String uid;
  String poNumber;
  UUID workOrderId;
  UUID tradingPartnerId;
  UUID supplierQuoteId;
  PurchaseOrderStatus status;
  String currency;
  String paymentTerms;
  LocalDate expectedDelivery;
  BigDecimal totalAmount;
  Integer revisionNumber;
  String notes;
  PurchaseOrderModuleType moduleType;
  PurchaseOrderSpecs moduleSpecs;
  List<PurchaseOrderLineResponse> lines;

  @Value
  @Builder
  public static class PurchaseOrderLineResponse {
    UUID id;
    UUID productId;
    String productDesc;
    BigDecimal qty;
    String unit;
    BigDecimal unitPrice;
    String currency;
    BigDecimal totalPrice;
    PurchaseOrderSpecs moduleSpecs;
  }
}
