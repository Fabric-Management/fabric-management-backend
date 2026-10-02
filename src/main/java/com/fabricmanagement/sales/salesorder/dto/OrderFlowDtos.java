package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.DeliveryEvent;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sales → planning → customer approval: the order's flow stage, planning's proposal and history.
 */
public final class OrderFlowDtos {

  private OrderFlowDtos() {}

  @Schema(name = "OrderFlowReason")
  public record Reason(@Size(max = 1000) String reason) {}

  @Schema(name = "ReopenOrderEvaluation")
  public record Reopen(@jakarta.validation.constraints.NotBlank @Size(max = 1000) String reason) {}

  /** Planning's proposed date for the delivery term's event, valid until a set time. */
  @Schema(name = "ProposeDelivery")
  public record ProposeDelivery(
      @NotNull LocalDate proposedOn, @NotNull Instant validUntil, @Size(max = 1000) String note) {}

  @Schema(name = "DeliveryProposalView")
  public record ProposalView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sequence,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate proposedOn,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant validUntil,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DeliveryTerm deliveryTerm,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String deliveryPlace,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) IncotermsVersion incotermsVersion,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DeliveryEvent deliveryEvent,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String note,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID proposedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant proposedAt,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Made under the order's current delivery term and place")
          boolean appliesToCurrentTerms,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean expired,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Made before the evaluation was reopened; propose or confirm again")
          boolean madeBeforeReopen) {}

  @Schema(name = "OrderFlowEventView")
  public record EventView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderFlowStage fromStage,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderFlowStage toStage,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String reason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID actorId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt) {}

  @Schema(name = "OrderFlowView")
  public record FlowView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderFlowStage stage,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ProposalView proposal,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Planning team and the planner responsible, once handed over")
          OrderWorkDtos.AssignmentView planning,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EventView> events) {}

  @Schema(name = "PlanningQueueLine")
  public record QueueLine(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID lineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String productDesc,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal requestedQty,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String unit,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          LocalDate requestedDeliveryDate) {}

  /** An order with planning: what planning needs to evaluate it and its current proposal. */
  @Schema(name = "PlanningQueueItem")
  public record QueueItem(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderNumber,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String customerName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderFlowStage stage,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          LocalDate requestedDeliveryDate,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          DeliveryTerm deliveryTerm,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String deliveryPlace,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          IncotermsVersion incotermsVersion,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          DeliveryTermStatus deliveryTermStatus,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          DeliveryEvent deliveryEvent,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QueueLine> lines,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ProposalView proposal,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Documents added since the order was handed to planning")
          long documentsAddedDuringPlanning,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          OrderWorkDtos.AssignmentView assignment,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "What the current user may do; a null reason means allowed")
          List<OrderWorkDtos.Capability> actions) {}
}
