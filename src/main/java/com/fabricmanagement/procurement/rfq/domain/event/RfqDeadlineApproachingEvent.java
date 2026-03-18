package com.fabricmanagement.procurement.rfq.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** RFQ deadline'ına yaklaşıyor — HIGH. */
@Getter
public class RfqDeadlineApproachingEvent extends DomainEvent {

  private final UUID rfqId;
  private final String rfqNumber;
  private final int hoursRemaining;

  public RfqDeadlineApproachingEvent(
      UUID tenantId, UUID rfqId, String rfqNumber, int hoursRemaining) {
    super(tenantId, "RFQ_DEADLINE_APPROACHING");
    this.rfqId = rfqId;
    this.rfqNumber = rfqNumber;
    this.hoursRemaining = hoursRemaining;
  }
}
