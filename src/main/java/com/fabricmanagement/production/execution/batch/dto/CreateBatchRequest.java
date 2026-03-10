package com.fabricmanagement.production.execution.batch.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request for creating a batch. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBatchRequest {

  private Long version;

  @NotNull(message = "Material ID is required")
  private UUID materialId;

  @NotNull(message = "Material type is required")
  private com.fabricmanagement.production.masterdata.material.domain.MaterialType materialType;

  /**
   * Generic JSONB attributes. For FIBER, use fiber-prefixed keys or the optional fiber fields
   * below.
   */
  private java.util.Map<String, Object> attributes;

  @NotBlank(message = "Batch code is required")
  private String batchCode;

  private String supplierBatchCode;

  @NotNull(message = "Quantity is required")
  @DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
  private BigDecimal quantity;

  @NotBlank(message = "Unit is required")
  private String unit;

  private Instant productionDate;

  private Instant expiryDate;

  private UUID locationId;

  private String remarks;

  // ── Optional fiber-specific fields (mapped to attributes with "fiber_" prefix when materialType
  // = FIBER) ──

  private Double micronaire;
  private Double stapleLength;
  private String fiberGrade;
  private String fiberShade;
  private String organicCertNo;
}
