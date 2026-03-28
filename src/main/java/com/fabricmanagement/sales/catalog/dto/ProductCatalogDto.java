package com.fabricmanagement.sales.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Value;

@Value
public class ProductCatalogDto {
  UUID id;
  UUID materialId;
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
