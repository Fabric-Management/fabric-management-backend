package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.ModuleType;
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSalesOrderLineRequest {

  /** Null indicates a new line, non-null indicates an update to an existing line. */
  private UUID id;

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
  private Map<String, Object> moduleSpecs;

  @AssertTrue(message = "Either productId or productDesc must be provided")
  public boolean isProductOrDescPresent() {
    return productId != null || (productDesc != null && !productDesc.isBlank());
  }
}
