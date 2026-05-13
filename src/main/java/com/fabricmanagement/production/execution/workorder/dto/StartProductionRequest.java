package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartProductionRequest {

  @NotEmpty(message = "Consumption batches list cannot be empty")
  @Valid
  private List<WorkOrderConsumptionDto> consumptions;

  @NotNull(message = "Output location ID is required")
  private UUID outputLocationId;

  /**
   * The Product that the blended output batch will be linked to. This is NOT the recipe ID — it
   * must be a valid Product entity ID. Example: the Yarn product produced from this blend.
   */
  @NotNull(message = "Output product ID is required")
  private UUID outputProductId;

  /**
   * The product type of the output batch (e.g. YARN, FABRIC). Must correspond to the
   * outputProductId's actual type.
   */
  @NotNull(message = "Output product type is required")
  private ProductType outputProductType;

  /** Optional remarks for the generated output batch. */
  private String remarks;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class WorkOrderConsumptionDto {

    @NotNull(message = "Batch ID is required for consumption")
    private UUID batchId;

    @NotNull(message = "Quantity to consume is required")
    @DecimalMin(value = "0.01", message = "Consumption quantity must be greater than zero")
    private BigDecimal quantity;

    /**
     * The percentage this batch contributes to the blend. All consumptions in a request must sum to
     * exactly 100. Must match the recipe's component ratios (e.g. 60 for Cotton, 40 for Abaca).
     */
    @NotNull(message = "Consumption percentage is required")
    @DecimalMin(value = "0.01", message = "Consumption percentage must be greater than zero")
    @DecimalMax(value = "100.00", message = "Consumption percentage cannot exceed 100")
    private BigDecimal consumptionPercentage;
  }
}
