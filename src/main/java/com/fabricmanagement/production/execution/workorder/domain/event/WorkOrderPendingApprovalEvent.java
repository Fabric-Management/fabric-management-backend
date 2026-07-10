package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * WorkOrder onay bekliyor — FlowBoard'a APPROVAL görevi düşer, yöneticiye bildirim gider. Önem:
 * HIGH
 */
@Getter
public class WorkOrderPendingApprovalEvent extends DomainEvent {

  private final UUID workOrderId;
  private final String workOrderNumber;
  private final UUID assignedToUserId; // onaylaması gereken kişi

  @JsonCreator
  public WorkOrderPendingApprovalEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("workOrderId") UUID workOrderId,
      @JsonProperty("workOrderNumber") String workOrderNumber,
      @JsonProperty("assignedToUserId") UUID assignedToUserId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "WORK_ORDER_PENDING_APPROVAL",
        occurredAt,
        correlationId);
    this.workOrderId = workOrderId;
    this.workOrderNumber = workOrderNumber;
    this.assignedToUserId = assignedToUserId;
  }

  public WorkOrderPendingApprovalEvent(
      UUID tenantId, UUID workOrderId, String workOrderNumber, UUID assignedToUserId) {
    super(tenantId, "WORK_ORDER_PENDING_APPROVAL");
    this.workOrderId = workOrderId;
    this.workOrderNumber = workOrderNumber;
    this.assignedToUserId = assignedToUserId;
  }
}
