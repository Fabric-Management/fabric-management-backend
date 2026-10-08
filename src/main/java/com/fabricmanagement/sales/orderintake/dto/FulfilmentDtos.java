package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.sales.orderintake.domain.CoverPortionKind;
import com.fabricmanagement.sales.orderintake.domain.LinePortionReadiness;
import com.fabricmanagement.sales.orderintake.domain.OrderArrivalEstimate;
import com.fabricmanagement.sales.salesorder.domain.LineShipmentPreference;
import com.fabricmanagement.sales.salesorder.dto.OrderWorkDtos;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Mixed cover, readiness and arrival contracts (SOI D5, D6). */
public final class FulfilmentDtos {

  private FulfilmentDtos() {}

  @Schema(name = "ConfirmGreigeCoverRequest")
  public record ConfirmGreigeCover(
      @NotNull @DecimalMin("0.001") BigDecimal finishedQuantity,
      @Size(max = 20) List<UUID> greigeBatchIds,
      @NotBlank @Size(max = 4000) String basisNote) {}

  @Schema(name = "RequestPortionReadinessRequest")
  public record RequestReadiness(@NotNull CoverPortionKind portion) {}

  @Schema(name = "ConfirmPortionReadinessRequest")
  public record ConfirmReadiness(
      @NotNull LocalDate readyOn, @NotBlank @Size(max = 4000) String basisNote) {}

  @Schema(name = "RecordArrivalEstimateRequest")
  public record RecordArrivalEstimate(
      @NotNull LocalDate earliestOn,
      @NotNull LocalDate latestOn,
      @NotNull OrderArrivalEstimate.Source source,
      @NotBlank @Size(max = 500) String sourceReference) {}

  @Schema(name = "HistoryEstimate")
  public record HistoryEstimate(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sampleSize,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          BigDecimal averageYieldPercent,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          BigDecimal minYieldPercent,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          BigDecimal maxYieldPercent,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long minLeadDays,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long medianLeadDays,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long maxLeadDays,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant basisAt) {}

  @Schema(name = "CoverPortionOutlook")
  public record PortionOutlook(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CoverPortionKind portion,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Line unit; null when it cannot be stated")
          BigDecimal quantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean quantityConfirmed,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          LinePortionReadiness.Status readinessStatus,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDate readyOn,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String readinessBasis,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID confirmedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant confirmedAt) {}

  @Schema(name = "LineCoverOutlook")
  public record LineOutlook(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderLineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal openQuantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unit,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "The line's shipment preference as recorded; shown to planning, not a shipment"
                      + " gate (LINE-PREFERENCES-1)")
          LineShipmentPreference shipmentPreference,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PortionOutlook> portions,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Latest confirmed portion date; null until every portion is confirmed")
          LocalDate readyOn,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description =
                  "COVER_EXCEEDS_OPEN when planned portions exceed the outstanding quantity")
          String blockingReason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) HistoryEstimate history) {}

  @Schema(name = "ArrivalEstimateView")
  public record ArrivalView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate earliestOn,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate latestOn,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderArrivalEstimate.Source source,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceReference,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID recordedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt) {}

  @Schema(name = "OrderDeliveryOutlook")
  public record DeliveryOutlook(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LineOutlook> lines,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Null: arrival unknown; never derived from readiness")
          ArrivalView arrival,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Unfinished custom requests of this order, attached or taken off")
          List<UUID> pendingCustomRequestIds) {}

  @Schema(name = "PortionReadinessRequest")
  public record ReadinessRequestView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderLineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CoverPortionKind portion,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID requestedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant requestedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderNumber,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Planning for production portions, the warehouse for held stock")
          OrderWorkDtos.AssignmentView assignment,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "What the current user may do; a null reason means allowed")
          List<OrderWorkDtos.Capability> actions) {}

  /** An order in fulfilment whose arrival estimate shipping keeps. */
  @Schema(name = "ArrivalWorkItem")
  public record ArrivalWorkItem(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderNumber,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderStatus,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ArrivalView arrival,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderWorkDtos.AssignmentView assignment,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          List<OrderWorkDtos.Capability> actions) {}

  @Schema(name = "CorrectLineProductRequest")
  public record CorrectProduct(
      @NotNull UUID fromProductId,
      @NotNull UUID toProductId,
      @NotNull @Size(min = 1, max = 100) List<@NotNull LineVersion> lines,
      @Size(max = 2000) String reason) {}

  @Schema(name = "LineVersionRef")
  public record LineVersion(@NotNull UUID lineId, @NotNull Long expectedVersion) {}

  @Schema(name = "LineProductCorrection")
  public record CorrectionView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderLineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID oldProductId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID newProductId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String reason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID correctedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant correctedAt) {}

  @Schema(name = "RequestLineHoldRequest")
  public record RequestHold(@NotBlank @Size(max = 2000) String reason) {}

  @Schema(name = "LineHold")
  public record HoldView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID workOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String workOrderNumber,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String requestReason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID requestedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant requestedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String stopNote,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant confirmedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String resumeNote,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant resumedAt) {}
}
