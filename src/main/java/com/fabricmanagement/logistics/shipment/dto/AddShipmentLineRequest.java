package com.fabricmanagement.logistics.shipment.dto;

import com.fabricmanagement.logistics.shipment.domain.ShipmentUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddShipmentLineRequest {

  @NotNull(message = "Sales order line ID is required")
  private UUID salesOrderLineId;

  @NotNull(message = "Line number is required")
  @Positive(message = "Line number must be positive")
  private Integer lineNumber;

  @NotNull(message = "Quantity is required")
  @Positive(message = "Quantity must be positive")
  private BigDecimal quantity;

  @NotNull(message = "Unit is required")
  private ShipmentUnit unit;
}
