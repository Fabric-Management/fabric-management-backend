package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SalesOrderLineResponse {

  UUID id;
  String uid;
  UUID salesOrderId;
  UUID productId;
  String productDesc;
  BigDecimal requestedQty;
  BigDecimal shippedQty;
  String unit;
  BigDecimal unitPrice;
  String currency;
  ModuleType moduleType;
  Map<String, Object> moduleSpecs;
  SalesOrderLineStatus lineStatus;
  UUID recipeId;
}
