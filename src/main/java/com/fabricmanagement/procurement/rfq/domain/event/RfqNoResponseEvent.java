package com.fabricmanagement.procurement.rfq.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** RFQ'ya tedarikçi yanıt vermedi — HIGH. */
@Getter
public class RfqNoResponseEvent extends DomainEvent {

  private final UUID rfqId;
  private final String rfqNumber;
  private final UUID supplierId;
  private final String supplierName;

  public RfqNoResponseEvent(
      UUID tenantId, UUID rfqId, String rfqNumber, UUID supplierId, String supplierName) {
    super(tenantId, "RFQ_NO_RESPONSE");
    this.rfqId = rfqId;
    this.rfqNumber = rfqNumber;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
  }
}
