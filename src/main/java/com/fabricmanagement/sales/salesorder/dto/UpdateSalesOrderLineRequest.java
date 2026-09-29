package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.CatalogLineInput;
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSalesOrderLineRequest implements CatalogLineInput {

  /** Null indicates a new line, non-null indicates an update to an existing line. */
  private UUID id;

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

  @NotNull(message = "Requested quantity is required")
  @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
  private BigDecimal requestedQty;

  @NotNull(message = "Unit is required")
  private String unit;

  private BigDecimal unitPrice;
  private String currency;
  private ModuleType moduleType;

  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> moduleSpecs;

  /** Null preserves the current profile; UNSPECIFIED facet values perform explicit clears. */
  @Schema(description = "Typed requirement profile update; omitted value preserves current profile")
  private RequirementProfileInput requirementProfile;
}
