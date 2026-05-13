package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request for creating a blended batch (multiple parent batches → one child batch). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBlendedBatchRequest {

  @NotBlank(message = "Batch code is required")
  private String batchCode;

  @NotNull(message = "Product ID is required")
  private UUID productId;

  @NotNull(message = "Product type is required")
  private ProductType productType;

  @NotNull(message = "Quantity is required")
  @DecimalMin(value = "0.001", message = "Quantity must be at least 0.001")
  private BigDecimal quantity;

  @NotBlank(message = "Unit is required")
  private String unit;

  @NotNull(message = "Location ID is required")
  private UUID locationId;

  private String remarks;

  @NotNull(message = "Parents list is required")
  @Size(min = 2, message = "At least 2 parent batches are required for blending")
  @Valid
  private List<BlendParentRequest> parents;

  // ── Source Tracking ──
  private com.fabricmanagement.production.execution.batch.domain.BatchSourceType sourceType;
  private UUID sourceId;
}
