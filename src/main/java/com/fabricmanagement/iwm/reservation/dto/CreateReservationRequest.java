package com.fabricmanagement.iwm.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateReservationRequest {
  @NotNull private UUID salesOrderLineId;
  @NotNull private UUID locationId;
  @NotNull private UUID materialId;
  @NotBlank private String lotNumber;
  private UUID goodsReceiptItemId;
  @NotNull @Positive private BigDecimal qtyReserved;
}
