package com.fabricmanagement.procurement.purchaseorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** PO teslimatı gecikiyor — HIGH önem. Tedarik sorumlusuna bildirim. */
@Getter
public class PoDeliveryLateEvent extends DomainEvent {

  private final UUID purchaseOrderId;
  private final String poNumber;
  private final UUID supplierId;
  private final String supplierName;
  private final Instant expectedDeliveryAt;
  private final int lateDays;

  public PoDeliveryLateEvent(
      UUID tenantId,
      UUID purchaseOrderId,
      String poNumber,
      UUID supplierId,
      String supplierName,
      Instant expectedDeliveryAt,
      int lateDays) {
    super(tenantId, "PO_DELIVERY_LATE");
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
    this.expectedDeliveryAt = expectedDeliveryAt;
    this.lateDays = lateDays;
  }
}
