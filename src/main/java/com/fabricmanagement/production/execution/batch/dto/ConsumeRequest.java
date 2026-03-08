package com.fabricmanagement.production.execution.batch.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for consuming batch quantity.
 *
 * <p>When {@code reservationId} is provided, consumption is drawn from that specific reservation's
 * reserved stock. When omitted, consumption is drawn from unreserved available stock only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumeRequest {

  private Long version;

  @NotNull(message = "Quantity is required")
  @DecimalMin(value = "0.001", message = "Quantity must be at least 0.001")
  private BigDecimal quantity;

  private UUID reservationId;

  private String remarks;
}
