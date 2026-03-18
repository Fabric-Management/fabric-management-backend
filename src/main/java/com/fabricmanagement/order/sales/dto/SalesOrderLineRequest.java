package com.fabricmanagement.order.sales.dto;

import com.fabricmanagement.order.sales.domain.ModuleType;
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

  private UUID materialId;

  private String productDesc;

  @NotNull(message = "Requested quantity is required")
  @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
  private BigDecimal requestedQty;

  @NotNull(message = "Unit is required")
  private String unit;

  private BigDecimal unitPrice;
  private String currency;
  private ModuleType moduleType;
  private Map<String, Object> moduleSpecs;

  /**
   * Validates that at least one of materialId or productDesc is provided. Triggered automatically
   * by Bean Validation via @Valid on parent request.
   */
  @AssertTrue(message = "Either materialId or productDesc must be provided")
  public boolean isMaterialOrDescPresent() {
    return materialId != null || (productDesc != null && !productDesc.isBlank());
  }
}
