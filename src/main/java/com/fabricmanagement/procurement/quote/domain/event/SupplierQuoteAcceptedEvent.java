package com.fabricmanagement.procurement.quote.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

@Getter
public class SupplierQuoteAcceptedEvent extends DomainEvent {

  private final UUID quoteId;
  private final UUID rfqId;

  public SupplierQuoteAcceptedEvent(UUID tenantId, UUID quoteId, UUID rfqId) {
    super(tenantId, "SUPPLIER_QUOTE_ACCEPTED");
    this.quoteId = quoteId;
    this.rfqId = rfqId;
  }
}
