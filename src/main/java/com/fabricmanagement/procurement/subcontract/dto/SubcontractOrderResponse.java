package com.fabricmanagement.procurement.subcontract.dto;

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
}
