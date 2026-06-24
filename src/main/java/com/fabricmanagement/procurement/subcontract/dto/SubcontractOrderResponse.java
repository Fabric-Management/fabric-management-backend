package com.fabricmanagement.procurement.subcontract.dto;

import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrder;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrderStatus;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SubcontractOrderResponse {
  UUID id;
  String uid;
  String scNumber;
  UUID workOrderId;
  UUID tradingPartnerId;
  SubcontractOrderStatus status;
  UUID inputProductId;
  ProductType inputProductType;
  UUID outputProductId;
  ProductType outputProductType;
  BigDecimal productSentQty;
  String unit;
  BigDecimal expectedOutputQty;
  String outputUnit;
  BigDecimal actualReturnedQty;
  BigDecimal wasteQty;
  BigDecimal agreedUnitPrice;
  String currency;
  LocalDate expectedReturnDate;
  String notes;

  public static SubcontractOrderResponse from(SubcontractOrder sc) {
    return SubcontractOrderResponse.builder()
        .id(sc.getId())
        .uid(sc.getUid())
        .scNumber(sc.getScNumber())
        .workOrderId(sc.getWorkOrderId())
        .tradingPartnerId(sc.getTradingPartnerId())
        .status(sc.getStatus())
        .inputProductId(sc.getInputProductId())
        .inputProductType(sc.getInputProductType())
        .outputProductId(sc.getOutputProductId())
        .outputProductType(sc.getOutputProductType())
        .productSentQty(sc.getProductSentQty())
        .unit(sc.getUnit())
        .expectedOutputQty(sc.getExpectedOutputQty())
        .outputUnit(sc.getOutputUnit())
        .actualReturnedQty(sc.getActualReturnedQty())
        .wasteQty(sc.getWasteQty())
        .agreedUnitPrice(sc.getAgreedUnitPrice())
        .currency(sc.getCurrency())
        .expectedReturnDate(sc.getExpectedReturnDate())
        .notes(sc.getNotes())
        .build();
  }
}
