package com.fabricmanagement.procurement.rfq.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

/** Fix #2 — İstemciden ham entity değil, bu DTO alınmalı. */
@Data
public class AddRfqLineRequest {

  private UUID materialId;

  private String productDesc;

  @NotNull(message = "Requested quantity is required")
  @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
  private BigDecimal requestedQty;

  @NotBlank(message = "Unit is required")
  private String unit;

  /** JSONB — certificationReq, originReq vb. */
  private String moduleSpecs;
}
