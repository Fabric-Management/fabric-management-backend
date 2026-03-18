package com.fabricmanagement.sales.api.dto;

import com.fabricmanagement.sales.domain.sample.DeliveryMethod;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class DispatchSampleRequest {

  @NotNull(message = "Delivery method is required")
  private DeliveryMethod deliveryMethod;

  /** Required for CARGO deliveries. */
  private String trackingNumber;

  /** Cargo company name (DHL, PTT, etc.) — required when trackingNumber is present. */
  private String cargoCompany;

  /** Salesperson user ID — required for SALESPERSON delivery method. */
  private UUID deliveredById;
}
