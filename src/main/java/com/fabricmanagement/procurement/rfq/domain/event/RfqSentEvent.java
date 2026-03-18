package com.fabricmanagement.procurement.rfq.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** RFQ tedarikçilere gönderildi — NORMAL. */
@Getter
public class RfqSentEvent extends DomainEvent {

  private final UUID rfqId;
  private final String rfqNumber;
  private final List<UUID> supplierIds;

  public RfqSentEvent(UUID tenantId, UUID rfqId, String rfqNumber, List<UUID> supplierIds) {
    super(tenantId, "RFQ_SENT");
    this.rfqId = rfqId;
    this.rfqNumber = rfqNumber;
    this.supplierIds = supplierIds != null ? List.copyOf(supplierIds) : List.of();
  }
}
