package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.sales.common.dto.SalesQuantityTermsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(
    name = "SalesStockPlanning",
    description =
        "Commercial requirements and physical ownership, never an inferred delivery readiness")
@com.fasterxml.jackson.annotation.JsonInclude(
    com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public record SalesStockPlanningDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID lineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesQuantityTermsDto quantityTerms,
    @Schema(nullable = true) BigDecimal customerRequestQuantity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal commercialQuantity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Assignment assignment,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Review review,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CutAssessment cutAssessment,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> heldPieceIds,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> allocatedPieceIds,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Capability> capabilities,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        com.fabricmanagement.production.core.stockunit.api.StockUnitCutPlanPort.Summary cutPlan) {
  @Schema(name = "SalesStockAssignment", enumAsRef = true)
  public enum Assignment {
    UNASSIGNED,
    LOT_QUANTITY_INTENT,
    ROLLS_PRE_RESERVED,
    ROLLS_ALLOCATED
  }

  @Schema(name = "SalesStockReview", enumAsRef = true)
  public enum Review {
    PLANNING_REQUIRED
  }

  @Schema(name = "SalesStockCutAssessment", enumAsRef = true)
  public enum CutAssessment {
    NOT_ASSESSED,
    EXACT_QUANTITY_REVIEW_REQUIRED
  }

  @Schema(name = "SalesStockPlanningAction", enumAsRef = true)
  public enum Action {
    SELECT_ROLLS,
    RECORD_QUANTITY_POLICY,
    APPLY_CUT_PLAN
  }

  @Schema(name = "SalesStockPlanningReason", enumAsRef = true)
  public enum Reason {
    WRITE_ACCESS_REQUIRED,
    DOCUMENT_NOT_EDITABLE,
    CUT_WORKFLOW_PENDING,
    EXACT_QUANTITY_REQUIRED
  }

  @Schema(name = "SalesStockPlanningCapability")
  public record Capability(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(nullable = true) Reason reason,
      @Schema(nullable = true) String routesTo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsApproval) {}
}
