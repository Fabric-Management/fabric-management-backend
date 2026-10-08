package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.CatalogLineInput;
import com.fabricmanagement.sales.salesorder.domain.LineShipmentPreference;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for adding a line to an existing SalesOrder (or embedded in create). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderLineRequest implements CatalogLineInput {

  @NotNull(message = "Product is required; the description never replaces it")
  private UUID productId;

  @Schema(description = "Optional line note; never a substitute for the product")
  private String productDesc;

  @Schema(description = "Colour card of this distribution (active tenant colour card)")
  private UUID colorId;

  @Schema(description = "Finished width; must be one of the product's defined widths")
  @DecimalMin(value = "0.01", message = "Finished width must be positive")
  private BigDecimal finishedWidth;

  @Schema(
      description = "Unit of the finished width",
      allowableValues = {"CM", "IN"})
  @jakarta.validation.constraints.Pattern(
      regexp = "CM|IN|cm|in",
      message = "Width unit must be CM or IN")
  private String finishedWidthUnit;

  @Schema(description = "Customer-requested delivery date of this distribution")
  private java.time.LocalDate requestedDeliveryDate;

  @Schema(description = "The customer requires this distribution from a single dye lot")
  private Boolean singleLotRequired;

  @Schema(
      description =
          "How the distribution may ship once ready; omitted means AS_READY (LINE-PREFERENCES-1)")
  private LineShipmentPreference shipmentPreference;

  @NotNull(message = "Requested quantity is required")
  @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
  private BigDecimal requestedQty;

  @NotNull(message = "Unit is required")
  private String unit;

  @Schema(description = "Agreed unit price in the line currency; omitted while not agreed")
  @DecimalMin(value = "0", message = "Unit price cannot be negative")
  private BigDecimal unitPrice;

  @Schema(description = "Agreed sales currency of this line (ISO 4217)", example = "USD")
  @jakarta.validation.constraints.Pattern(
      regexp = "[A-Z]{3}",
      message = "Currency must be a 3-letter ISO code")
  private String currency;

  @Schema(description = "Discount on this line in the line currency; needs a unit price")
  @DecimalMin(value = "0", message = "Discount cannot be negative")
  private BigDecimal discountAmount;

  @Schema(description = "Tax on this line in the line currency; needs a unit price")
  @DecimalMin(value = "0", message = "Tax cannot be negative")
  private BigDecimal taxAmount;

  @Schema(description = "Quantity tolerance above the requested quantity agreed for this line (%)")
  @DecimalMin(value = "0", message = "A tolerance cannot be negative")
  @jakarta.validation.constraints.DecimalMax(
      value = "100",
      message = "A tolerance cannot exceed 100 percent")
  private BigDecimal toleranceUpPct;

  @Schema(description = "Quantity tolerance below the requested quantity agreed for this line (%)")
  @DecimalMin(value = "0", message = "A tolerance cannot be negative")
  @jakarta.validation.constraints.DecimalMax(
      value = "100",
      message = "A tolerance cannot exceed 100 percent")
  private BigDecimal toleranceDownPct;

  private ModuleType moduleType;

  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> moduleSpecs;

  @Schema(description = "Typed, traceable order-line requirement profile input")
  private RequirementProfileInput requirementProfile;
}
