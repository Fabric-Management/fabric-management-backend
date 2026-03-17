package com.fabricmanagement.order.sales.dto;

import com.fabricmanagement.order.sales.domain.ModuleType;
import com.fabricmanagement.order.sales.domain.SalesOrderLineStatus;
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
  UUID materialId;
  String productDesc;
  BigDecimal requestedQty;
  String unit;
  BigDecimal unitPrice;
  String currency;
  ModuleType moduleType;
  Map<String, Object> moduleSpecs;
  SalesOrderLineStatus lineStatus;
  UUID recipeId;
}
