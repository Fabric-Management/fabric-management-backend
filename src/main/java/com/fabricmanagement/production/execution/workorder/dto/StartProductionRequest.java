package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
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

  /**
   * @deprecated Phase 2 refactoring: Input consumption is moved to separate action.
   */
  @Deprecated(forRemoval = true)
  @jakarta.validation.constraints.Null(
      message = "Input consumption is moved to separate action. Use consumption endpoints.")
  private List<WorkOrderConsumptionDto> consumptions;

  /**
   * @deprecated Phase 2 refactoring: Lot creation (and its location) is moved to separate action.
   */
  @Deprecated(forRemoval = true)
  @jakarta.validation.constraints.Null(
      message = "Lot creation is moved to separate action. Use openLot endpoint.")
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

    /**
     * @deprecated Phase 2 refactoring: Input consumption is moved to separate action.
     */
    @Deprecated(forRemoval = true)
    private UUID batchId;

    /**
     * @deprecated Phase 2 refactoring: Input consumption is moved to separate action.
     */
    @Deprecated(forRemoval = true)
    private BigDecimal quantity;

    /**
     * The percentage this batch contributes to the blend.
     *
     * @deprecated Phase 2 refactoring: Input consumption is moved to separate action.
     */
    @Deprecated(forRemoval = true)
    private BigDecimal consumptionPercentage;
  }
}
