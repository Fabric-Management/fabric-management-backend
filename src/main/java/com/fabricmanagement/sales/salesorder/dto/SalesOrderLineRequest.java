package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
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
public class SalesOrderLineRequest {

  private UUID productId;

  private String productDesc;

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

  /**
   * Validates that at least one of productId or productDesc is provided. Triggered automatically by
   * Bean Validation via @Valid on parent request.
   */
  @AssertTrue(message = "Either productId or productDesc must be provided")
  public boolean isProductOrDescPresent() {
    return productId != null || (productDesc != null && !productDesc.isBlank());
  }
}
