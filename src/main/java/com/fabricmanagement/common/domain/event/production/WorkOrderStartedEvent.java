package com.fabricmanagement.common.domain.event.production;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Published after a linked WorkOrder transitions to IN_PROGRESS. */
@Getter
public class WorkOrderStartedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final UUID salesOrderLineId;

  public WorkOrderStartedEvent(UUID tenantId, UUID workOrderId, UUID salesOrderLineId) {
    super(tenantId, "WORK_ORDER_STARTED");
    this.workOrderId = workOrderId;
    this.salesOrderLineId = salesOrderLineId;
  }

  @JsonCreator
  public WorkOrderStartedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("workOrderId") UUID workOrderId,
      @JsonProperty("salesOrderLineId") UUID salesOrderLineId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "WORK_ORDER_STARTED",
        occurredAt,
        correlationId);
    this.workOrderId = workOrderId;
    this.salesOrderLineId = salesOrderLineId;
  }
}
