package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.execution.workorder.domain.FulfillmentType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.specs.GenericProductionSpecs;
import com.fabricmanagement.production.execution.workorder.domain.specs.WorkOrderProductionSpecs;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder
public record WorkOrderRequest(
    UUID recipeId,
    @NotNull @Schema(description = "Output product UUID") UUID outputProductId,
    @NotNull @Schema(description = "Production module type (e.g. WEAVING, DYEING)")
        WorkOrderModuleType moduleType,
    @NotNull @Schema(description = "Module-specific production specifications")
        WorkOrderProductionSpecs productionSpecs,
    UUID tradingPartnerId,
    UUID salesOrderLineId,
    @NotNull(message = "Fulfillment type is mandatory") FulfillmentType fulfillmentType,
    @NotNull(message = "Planned quantity is mandatory")
        @DecimalMin(value = "0.01", message = "Planned quantity must be greater than zero")
        BigDecimal plannedQty,
    @NotBlank(message = "Unit is mandatory") String unit,
    BigDecimal unitCost,
    String currency,
    Instant deadline,
    String certificationReq,
    String originReq,
    String notes,
    List<Map<String, Object>> attachments) {

  public WorkOrderRequest {
    if (productionSpecs == null) {
      productionSpecs = new GenericProductionSpecs(null);
    }
  }
}
