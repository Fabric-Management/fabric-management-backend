package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceSentEvent extends DomainEvent {

  private final UUID invoiceId;
  private final String invoiceNumber;
  private final UUID tradingPartnerId;

  public InvoiceSentEvent(
      UUID tenantId, UUID invoiceId, String invoiceNumber, UUID tradingPartnerId) {
    super(tenantId, "INVOICE_SENT");
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.tradingPartnerId = tradingPartnerId;
  }
}
