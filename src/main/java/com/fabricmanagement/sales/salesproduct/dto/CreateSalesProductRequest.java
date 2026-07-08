package com.fabricmanagement.sales.salesproduct.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Value;

@Value
public class CreateSalesProductRequest {
  @NotNull UUID productId;

  @Schema(description = "Snapshot of the production product name shown in sales catalog pickers")
  @Size(max = 255)
  String productName;

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
