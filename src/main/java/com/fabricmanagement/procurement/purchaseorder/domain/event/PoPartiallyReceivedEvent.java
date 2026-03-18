package com.fabricmanagement.procurement.purchaseorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** PO kısmi teslim alındı — NORMAL. */
@Getter
public class PoPartiallyReceivedEvent extends DomainEvent {

  private final UUID purchaseOrderId;
  private final String poNumber;
  private final int receivedItemCount;
  private final int totalItemCount;

  public PoPartiallyReceivedEvent(
      UUID tenantId,
      UUID purchaseOrderId,
      String poNumber,
      int receivedItemCount,
      int totalItemCount) {
    super(tenantId, "PO_PARTIALLY_RECEIVED");
    this.purchaseOrderId = purchaseOrderId;
    this.poNumber = poNumber;
    this.receivedItemCount = receivedItemCount;
    this.totalItemCount = totalItemCount;
  }
}
