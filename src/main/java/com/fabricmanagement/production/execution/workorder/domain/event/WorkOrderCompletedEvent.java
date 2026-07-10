package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Published when a WorkOrder finishes production and transitions to COMPLETED. */
@Getter
public class WorkOrderCompletedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final UUID salesOrderLineId;
  private final String workOrderNumber;
  private final BigDecimal plannedQty;
  private final BigDecimal actualQty;
  private final BigDecimal totalConsumed;
  private final BigDecimal yieldPercentage;
  private final Instant completedAt;
  private final UUID completedBy;

  public WorkOrderCompletedEvent(
      UUID tenantId,
      UUID workOrderId,
      UUID salesOrderLineId,
      String workOrderNumber,
      BigDecimal plannedQty,
      BigDecimal actualQty,
      BigDecimal totalConsumed,
      BigDecimal yieldPercentage,
      Instant completedAt,
      UUID completedBy) {
    super(tenantId, "WORK_ORDER_COMPLETED");
    this.workOrderId = workOrderId;
    this.salesOrderLineId = salesOrderLineId;
    this.workOrderNumber = workOrderNumber;
    this.plannedQty = plannedQty;
    this.actualQty = actualQty;
    this.totalConsumed = totalConsumed;
    this.yieldPercentage = yieldPercentage;
    this.completedAt = completedAt;
    this.completedBy = completedBy;
  }

  @JsonCreator
  public WorkOrderCompletedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("workOrderId") UUID workOrderId,
      @JsonProperty("salesOrderLineId") UUID salesOrderLineId,
      @JsonProperty("workOrderNumber") String workOrderNumber,
      @JsonProperty("plannedQty") BigDecimal plannedQty,
      @JsonProperty("actualQty") BigDecimal actualQty,
      @JsonProperty("totalConsumed") BigDecimal totalConsumed,
      @JsonProperty("yieldPercentage") BigDecimal yieldPercentage,
      @JsonProperty("completedAt") Instant completedAt,
      @JsonProperty("completedBy") UUID completedBy) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "WORK_ORDER_COMPLETED",
        occurredAt,
        correlationId);
    this.workOrderId = workOrderId;
    this.salesOrderLineId = salesOrderLineId;
    this.workOrderNumber = workOrderNumber;
    this.plannedQty = plannedQty;
    this.actualQty = actualQty;
    this.totalConsumed = totalConsumed;
    this.yieldPercentage = yieldPercentage;
    this.completedAt = completedAt;
    this.completedBy = completedBy;
  }
}
