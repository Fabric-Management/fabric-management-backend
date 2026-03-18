package com.fabricmanagement.procurement.purchaseorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** PO tedarikçi tarafından onaylandı — NORMAL. */
@Getter
public class PoConfirmedEvent extends DomainEvent {

  private final UUID purchaseOrderId;
  private final String poNumber;
  private final UUID supplierId;

  public PoConfirmedEvent(UUID tenantId, UUID purchaseOrderId, String poNumber, UUID supplierId) {
    super(tenantId, "PO_CONFIRMED");
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.supplierId = supplierId;
  }
}
