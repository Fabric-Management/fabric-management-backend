package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceDisputedEvent extends DomainEvent {

  private final UUID invoiceId;
  private final String invoiceNumber;
  private final UUID tradingPartnerId;

  public InvoiceDisputedEvent(
      UUID tenantId, UUID invoiceId, String invoiceNumber, UUID tradingPartnerId) {
    super(tenantId, "INVOICE_DISPUTED");
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.tradingPartnerId = tradingPartnerId;
  }
}
