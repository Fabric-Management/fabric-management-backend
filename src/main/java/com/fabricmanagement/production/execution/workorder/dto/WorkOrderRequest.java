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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// TODO(Technical Debt): Convert to record (AGENTS.md constraint) - pending large refactoring to
// update all getter usages
public class WorkOrderRequest {

  private UUID recipeId;

  @NotNull
  @Schema(description = "Output product UUID")
  private UUID outputProductId;

  @NotNull
  @Schema(description = "Production module type (e.g. WEAVING, DYEING)")
  private WorkOrderModuleType moduleType;

  @NotNull
  @Schema(description = "Module-specific production specifications")
  @Builder.Default
  private WorkOrderProductionSpecs productionSpecs = new GenericProductionSpecs(null);

  private UUID tradingPartnerId;

  private UUID salesOrderLineId;

  @NotNull(message = "Fulfillment type is mandatory")
  private FulfillmentType fulfillmentType;

  @NotNull(message = "Planned quantity is mandatory")
  @DecimalMin(value = "0.01", message = "Planned quantity must be greater than zero")
  private BigDecimal plannedQty;

  @NotBlank(message = "Unit is mandatory")
  private String unit;

  private BigDecimal unitCost;

  private String currency;

  private Instant deadline;

  private String notes;

  private List<Map<String, Object>> attachments;
}
