package com.fabricmanagement.sales.salesproduct.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Value;

@Value
public class SalesProductDto {
  UUID id;
  UUID productId;
  String moduleType;
  BigDecimal listPrice;
  String currency;
  BigDecimal moq;
  String moqUnit;
  Integer leadTimeDays;
  String specs;
  String photos;
  boolean isActive;
  UUID tenantId;
}
