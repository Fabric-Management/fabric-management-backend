package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.attributes.FiberAttributes;
import com.fabricmanagement.production.execution.batch.domain.attributes.YarnAttributes;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
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
@Schema(description = "Request object for creating a new product batch")
public class CreateBatchRequest {

  @Schema(description = "Optimistic locking version")
  private Long version;

  @NotNull(message = "Product ID is required")
  @Schema(
      description = "ID of the parent product this batch belongs to",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID productId;

  @NotNull(message = "Product type is required")
  @Schema(
      description = "Type of the product (FIBER, YARN, FABRIC)",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private ProductType productType;

  @NotBlank(message = "Batch code is required")
  @Schema(
      description = "Internal unique batch code/lot number",
      example = "B-2026-001",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String batchCode;

  @Schema(description = "Supplier's batch code/lot number", example = "SUP-L44")
  private String supplierBatchCode;

  @NotNull(message = "Quantity is required")
  @DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
  @Schema(
      description = "Initial quantity of the batch",
      example = "1500.50",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private BigDecimal quantity;

  @NotBlank(message = "Unit is required")
  @Schema(
      description = "Unit of measure (e.g., KG, MTR)",
      example = "KG",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String unit;

  @Schema(description = "Date when the batch was produced")
  private Instant productionDate;

  @Schema(description = "Expiration date if applicable")
  private Instant expiryDate;

  @Schema(description = "ID of the warehouse location where the batch is stored")
  private UUID locationId;

  /**
   * Optional FiberQualityStandard for QC. When null, default profile for batch's ISO code is
   * applied. If no default exists, profile selection is skipped.
   */
  @Schema(
      description =
          "Optional ID of the quality standard to apply. If omitted, the default for the product's ISO code is used.")
  private UUID qualityStandardId;

  @Schema(description = "Additional remarks/notes")
  private String remarks;

  @Schema(
      description = "Detailed specifications. REQUIRED if productType is FIBER. Ignored otherwise.",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @Valid
  private FiberAttributes fiberSpecs;

  @Schema(
      description = "Detailed specifications. REQUIRED if productType is YARN. Ignored otherwise.",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @Valid
  private YarnAttributes yarnSpecs;

  /**
   * Batch-level composition override (FIBER only). When present, stored in attributes and takes
   * precedence over Fiber.composition. Map of baseFiberId (UUID) → percentage (BigDecimal). Omit to
   * use Fiber default.
   */
  @Schema(
      description =
          "Batch-level composition override (FIBER only). Map of Base Fiber ID to percentage. Overrides Product default.")
  private Map<UUID, BigDecimal> composition;

  // ── Source Tracking ──
  @Schema(
      description =
          "Type of the source that created this batch (e.g., PURCHASE_ORDER, PRODUCTION_ORDER)")
  private com.fabricmanagement.production.execution.batch.domain.BatchSourceType sourceType;

  @Schema(description = "ID of the source entity that created this batch")
  private UUID sourceId;
}
