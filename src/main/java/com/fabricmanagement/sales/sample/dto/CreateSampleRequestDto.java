package com.fabricmanagement.sales.sample.dto;

import com.fabricmanagement.sales.sample.domain.DeliveryMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Value;

@Value
public class CreateSampleRequestDto {
  @NotNull UUID customerId;
  @NotNull UUID productId;

  @NotNull
  @DecimalMin("0.0")
  BigDecimal requestedQty;

  @NotBlank String unit;

  @NotNull DeliveryMethod deliveryMethod;

  String deliveryAddress;
  UUID salesOrderId;
  String notes;
}
