package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** WorkOrder deadline atandı — FlowBoard PriorityScore günceller. Önem: NORMAL */
@Getter
public class WorkOrderDeadlineSetEvent extends DomainEvent {

  private final UUID workOrderId;
  private final String workOrderNumber;
  private final Instant deadline;

  @JsonCreator
  public WorkOrderDeadlineSetEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("workOrderId") UUID workOrderId,
      @JsonProperty("workOrderNumber") String workOrderNumber,
      @JsonProperty("deadline") Instant deadline) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "WORK_ORDER_DEADLINE_SET",
        occurredAt,
        correlationId);
    this.workOrderId = workOrderId;
    this.workOrderNumber = workOrderNumber;
    this.deadline = deadline;
  }

  public WorkOrderDeadlineSetEvent(
      UUID tenantId, UUID workOrderId, String workOrderNumber, Instant deadline) {
    super(tenantId, "WORK_ORDER_DEADLINE_SET");
    this.workOrderId = workOrderId;
    this.workOrderNumber = workOrderNumber;
    this.deadline = deadline;
  }
}
