package com.fabricmanagement.production.core.stockunit.dto;

import com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort.TargetType;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlanState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StockUnitCutPlanDtos {
  private StockUnitCutPlanDtos() {}

  @Schema(name = "StockUnitCutPlanAction", enumAsRef = true)
  public enum Action {
    APPLY,
    APPROVE,
    RECORD,
    CORRECT,
    VERIFY,
    CANCEL
  }

  @Schema(name = "StockUnitCutPlanReason", enumAsRef = true)
  public enum Reason {
    WRITE_REQUIRED,
    PRODUCTION_WRITE_REQUIRED,
    TARGET_NOT_EDITABLE,
    EXECUTION_NOT_RELEASED,
    TARGET_CHANGED,
    SOURCE_CHANGED,
    REVIEW_REQUIRED,
    ACTUAL_REQUIRED,
    MEASUREMENTS_DISPUTED,
    PLAN_ENDED
  }

  @Schema(name = "StockUnitCutPlanCapability")
  public record Capability(
      @NotNull Action action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Reason reason,
      @NotNull String routesTo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsApproval) {}

  @Schema(name = "StockUnitCutTargetView")
  public record TargetView(
      @NotNull TargetType targetType,
      @NotNull UUID documentId,
      @NotNull UUID lineId,
      @NotNull UUID productId,
      @NotNull String reference,
      @NotNull Long documentVersion,
      @NotNull BigDecimal deliveryQuantity,
      @NotNull String unit,
      @NotNull String liveEventsPath,
      @NotNull List<Capability> capabilities) {}

  @Schema(name = "StockUnitCutPlanSource")
  public record Source(
      @NotNull UUID stockUnitId,
      @NotNull UUID batchId,
      @NotNull Long stockVersion,
      @NotNull Long measurementVersion,
      @NotNull BigDecimal length,
      @NotNull BigDecimal weight,
      @NotNull String weightUnit,
      @Schema(nullable = true) String serialNumber,
      @Schema(nullable = true) UUID qualityGradeId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cutSource) {}

  @Schema(name = "StockUnitCutActualMeasurements")
  public record Actual(
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3)
          BigDecimal deliveryLength,
      @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal remainderLength,
      @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal lossLength,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3)
          BigDecimal deliveryWeight,
      @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal remainderWeight,
      @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal lossWeight,
      @Size(max = 1000) @Schema(nullable = true) String reason) {}

  @Schema(name = "StockUnitCutPlanCommand")
  public record Command(
      @NotNull UUID operationId,
      @NotNull @PositiveOrZero Long expectedVersion,
      @Valid @Schema(nullable = true) Actual actual,
      @Size(max = 1000) @Schema(nullable = true) String reason) {}

  @Schema(name = "StockUnitCutPlanView")
  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
  public record View(
      @Schema(nullable = true) UUID planId,
      @Schema(nullable = true) Long version,
      @NotNull TargetType targetType,
      @NotNull UUID documentId,
      @NotNull UUID lineId,
      @Schema(nullable = true, implementation = StockUnitCutPlanState.class, enumAsRef = true)
          StockUnitCutPlanState state,
      @NotNull BigDecimal sourceQuantity,
      @NotNull BigDecimal deliveryQuantity,
      @NotNull BigDecimal cutDeliveryQuantity,
      @NotNull BigDecimal remainderQuantity,
      @NotNull String unit,
      @NotNull List<@Valid Source> sources,
      @Valid @Schema(nullable = true) Actual actual,
      @Schema(nullable = true) UUID deliveryStockUnitId,
      @Schema(nullable = true) UUID remainderStockUnitId,
      @Schema(nullable = true) String deliverySerialNumber,
      @Schema(nullable = true) String remainderSerialNumber,
      @Schema(nullable = true) UUID preparedBy,
      @Schema(nullable = true) Instant preparedAt,
      @Schema(nullable = true) UUID reviewedBy,
      @Schema(nullable = true) Instant reviewedAt,
      @Schema(nullable = true) UUID recordedBy,
      @Schema(nullable = true) Instant recordedAt,
      @Schema(nullable = true) UUID verifiedBy,
      @Schema(nullable = true) Instant verifiedAt,
      @Schema(nullable = true) String dispositionReason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean physicalDispositionPending,
      @NotNull List<@Valid Capability> capabilities) {}
}
