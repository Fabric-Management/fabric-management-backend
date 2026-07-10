package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** WorkOrder onaylandı — üretim başlayabilir. Önem: NORMAL */
@Getter
public class WorkOrderApprovedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final String workOrderNumber;
  private final WorkOrderModuleType moduleType;
  private final UUID outputProductId;
  private final BigDecimal plannedQuantity;
  private final UUID tradingPartnerId;
  private final UUID approvedByUserId;

  public WorkOrderApprovedEvent(
      UUID tenantId,
      UUID workOrderId,
      String workOrderNumber,
      WorkOrderModuleType moduleType,
      UUID outputProductId,
      BigDecimal plannedQuantity,
      UUID tradingPartnerId,
      UUID approvedByUserId) {
    super(tenantId, "WORK_ORDER_APPROVED");
    this.workOrderId = workOrderId;
    this.workOrderNumber = workOrderNumber;
    this.moduleType = moduleType;
    this.outputProductId = outputProductId;
    this.plannedQuantity = plannedQuantity;
    this.tradingPartnerId = tradingPartnerId;
    this.approvedByUserId = approvedByUserId;
  }

  @JsonCreator
  public WorkOrderApprovedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("workOrderId") UUID workOrderId,
      @JsonProperty("workOrderNumber") String workOrderNumber,
      @JsonProperty("moduleType") WorkOrderModuleType moduleType,
      @JsonProperty("outputProductId") UUID outputProductId,
      @JsonProperty("plannedQuantity") BigDecimal plannedQuantity,
      @JsonProperty("tradingPartnerId") UUID tradingPartnerId,
      @JsonProperty("approvedByUserId") UUID approvedByUserId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "WORK_ORDER_APPROVED",
        occurredAt,
        correlationId);
    this.workOrderId = workOrderId;
    this.workOrderNumber = workOrderNumber;
    this.moduleType = moduleType;
    this.outputProductId = outputProductId;
    this.plannedQuantity = plannedQuantity;
    this.tradingPartnerId = tradingPartnerId;
    this.approvedByUserId = approvedByUserId;
  }
}
