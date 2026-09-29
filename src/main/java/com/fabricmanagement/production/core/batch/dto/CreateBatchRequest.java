package com.fabricmanagement.production.core.batch.dto;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.batch.domain.attributes.FiberAttributes;
import com.fabricmanagement.production.core.batch.domain.attributes.YarnAttributes;
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

  @Schema(
      description = "Optional active tenant color-card ID; null means unassigned",
      nullable = true)
  private UUID colorId;

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
   * Optional tenant quality profile (FIBER only). It must apply to this fibre and effective
   * composition ({@code FIBER_QUALITY_TARGET_MISMATCH} otherwise). When null, the exact FIBER
   * default applies, then the ISO default for a pure fibre at 100%, else none (manual review).
   */
  @Schema(
      nullable = true,
      description =
          "FIBER only. Optional profile id; must apply to the fibre and effective composition"
              + " (409 FIBER_QUALITY_TARGET_MISMATCH). Omit to use the resolver's default; see"
              + " POST /fiber-quality-standards/applicable")
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
   * Optional physical composition override (FIBER only), keyed by component {@code Fiber.id} (never
   * Product ids). Validated like any composition; one pure component at exactly 100% is allowed.
   * Omit to use the fibre definition; an explicit empty map is invalid.
   */
  @Schema(
      nullable = true,
      description =
          "FIBER only. Physical composition override keyed by Fiber.id; exact decimal sum 100."
              + " Omit to use the fibre definition; an empty map is rejected")
  private Map<UUID, BigDecimal> composition;

  // ── Source Tracking ──
  @Schema(
      description =
          "Type of the source that created this batch (e.g., PURCHASE_ORDER, PRODUCTION_ORDER)")
  private com.fabricmanagement.production.core.batch.domain.BatchSourceType sourceType;

  @Schema(description = "ID of the source entity that created this batch")
  private UUID sourceId;
}
