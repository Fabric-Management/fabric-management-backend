package com.fabricmanagement.procurement.rfq.dto;

import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateSupplierRFQRequest {

  @NotNull(message = "Work order ID is required")
  private UUID workOrderId;

  @NotBlank(message = "Module type is required")
  private String moduleType;

  @NotNull(message = "RFQ type is required")
  private SupplierRFQType rfqType;

  @NotNull(message = "Deadline is required")
  @Future(message = "Deadline must be in the future")
  private Instant deadline;

  private String notes;
}
