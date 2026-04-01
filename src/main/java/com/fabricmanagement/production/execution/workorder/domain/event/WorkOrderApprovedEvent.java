package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/** WorkOrder onaylandı — üretim başlayabilir. Önem: NORMAL */
@Getter
public class WorkOrderApprovedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final String workOrderNumber;
  private final String moduleType;
  private final UUID outputMaterialId;
  private final BigDecimal plannedQuantity;
  private final UUID tradingPartnerId;
  private final UUID approvedByUserId;

  public WorkOrderApprovedEvent(
      UUID tenantId,
      UUID workOrderId,
      String workOrderNumber,
      String moduleType,
      UUID outputMaterialId,
      BigDecimal plannedQuantity,
      UUID tradingPartnerId,
      UUID approvedByUserId) {
    super(tenantId, "WORK_ORDER_APPROVED");
    this.workOrderId = workOrderId;
    this.workOrderNumber = workOrderNumber;
    this.moduleType = moduleType;
    this.outputMaterialId = outputMaterialId;
    this.plannedQuantity = plannedQuantity;
    this.tradingPartnerId = tradingPartnerId;
    this.approvedByUserId = approvedByUserId;
  }
}
