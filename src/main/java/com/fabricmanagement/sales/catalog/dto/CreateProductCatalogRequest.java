package com.fabricmanagement.sales.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Value;

@Value
public class CreateProductCatalogRequest {
  @NotNull UUID materialId;

  @NotBlank String moduleType;

  @NotNull
  @DecimalMin("0.0")
  BigDecimal listPrice;

  @NotBlank String currency;

  @DecimalMin("0.0")
  BigDecimal moq;

  String moqUnit;

  Integer leadTimeDays;

  // Json string
  String specs;

  // Json string
  String photos;
}
