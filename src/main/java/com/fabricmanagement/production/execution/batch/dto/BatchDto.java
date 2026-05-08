package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.attributes.FiberAttributes;
import com.fabricmanagement.production.execution.batch.domain.attributes.YarnAttributes;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Batch DTO. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response object representing a material batch in the system")
public class BatchDto {

  @Schema(description = "Unique batch ID")
  private UUID id;

  @Schema(description = "Tenant ID")
  private UUID tenantId;

  @Schema(description = "Human-readable unique identifier", example = "BAT-2026-0001")
  private String uid;

  @Schema(description = "ID of the parent material this batch belongs to")
  private UUID materialId;

  @Schema(description = "Type of the material (FIBER, YARN, FABRIC)")
  private MaterialType materialType;

  @Schema(
      description = "Raw JSONB attributes map. Prefer using fiberSpecs/yarnSpecs when available.")
  private java.util.Map<String, Object> attributes;

  @Schema(description = "Internal unique batch code/lot number", example = "B-2026-001")
  private String batchCode;

  @Schema(description = "Supplier's batch code/lot number", example = "SUP-L44")
  private String supplierBatchCode;

  @Schema(description = "Initial quantity of the batch")
  private BigDecimal quantity;

  @Schema(description = "Quantity currently reserved for active orders")
  private BigDecimal reservedQuantity;

  @Schema(description = "Quantity consumed in production")
  private BigDecimal consumedQuantity;

  @Schema(description = "Quantity declared as waste")
  private BigDecimal wasteQuantity;

  @Schema(description = "Calculated available quantity (quantity - reserved - consumed - waste)")
  private BigDecimal availableQuantity;

  @Schema(description = "Net output quantity (if applicable)")
  private BigDecimal netOutputQuantity;

  @Schema(description = "Waste percentage calculated automatically")
  private BigDecimal wastePercentage;

  @Schema(description = "Unit of measure (e.g., KG, MTR)")
  private String unit;

  @Schema(description = "Date when the batch was produced")
  private Instant productionDate;

  @Schema(description = "Expiration date if applicable")
  private Instant expiryDate;

  @Schema(
      description = "Current lifecycle status of the batch (e.g., AVAILABLE, QUARANTINE, CONSUMED)")
  private BatchStatus status;

  @Schema(description = "ID of the warehouse location where the batch is stored")
  private UUID locationId;

  @Schema(description = "ID of the parent batch if this was split/transferred")
  private UUID parentBatchId;

  @Schema(description = "ID of the quality standard applied to this batch")
  private UUID qualityStandardId;

  @Schema(description = "Additional remarks/notes")
  private String remarks;

  /**
   * Resolved composition: Batch.attributes.composition if present, else Fiber.composition. Map of
   * baseFiberId → percentage. Empty for pure fibers. Only meaningful when materialType = FIBER.
   */
  @Schema(
      description =
          "Resolved composition. Map of Base Fiber ID to percentage. Empty for pure fibers.")
  private Map<UUID, BigDecimal> composition;

  @Schema(description = "Detailed specifications for FIBER batches. Null for other material types.")
  private FiberAttributes fiberSpecs;

  @Schema(description = "Detailed specifications for YARN batches. Null for other material types.")
  private YarnAttributes yarnSpecs;

  @Schema(description = "Whether the batch is active (not soft-deleted)")
  private Boolean isActive;

  @Schema(description = "Optimistic locking version")
  private Long version;

  @Schema(description = "Creation timestamp")
  private Instant createdAt;

  @Schema(description = "Last update timestamp")
  private Instant updatedAt;

  /** Map entity to DTO (composition not resolved; set separately). */
  public static BatchDto from(Batch entity) {
    BatchDto.BatchDtoBuilder b =
        BatchDto.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .uid(entity.getUid())
            .materialId(entity.getMaterialId())
            .materialType(entity.getMaterialType())
            .attributes(entity.getAttributes())
            .fiberSpecs(
                entity.getMaterialType() == MaterialType.FIBER
                    ? FiberAttributes.from(entity.getAttributes())
                    : null)
            .yarnSpecs(
                entity.getMaterialType() == MaterialType.YARN
                    ? YarnAttributes.from(entity.getAttributes())
                    : null)
            .batchCode(entity.getBatchCode())
            .supplierBatchCode(entity.getSupplierBatchCode())
            .quantity(entity.getQuantity())
            .reservedQuantity(entity.getReservedQuantity())
            .consumedQuantity(entity.getConsumedQuantity())
            .wasteQuantity(entity.getWasteQuantity())
            .availableQuantity(entity.getAvailableQuantity())
            .netOutputQuantity(entity.getNetOutputQuantity())
            .wastePercentage(entity.getWastePercentage())
            .unit(entity.getUnit())
            .productionDate(entity.getProductionDate())
            .expiryDate(entity.getExpiryDate())
            .status(entity.getStatus())
            .locationId(entity.getLocationId())
            .parentBatchId(entity.getParentBatchId())
            .qualityStandardId(entity.getQualityStandardId())
            .remarks(entity.getRemarks())
            .version(entity.getVersion())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt());
    return b.build();
  }
}
