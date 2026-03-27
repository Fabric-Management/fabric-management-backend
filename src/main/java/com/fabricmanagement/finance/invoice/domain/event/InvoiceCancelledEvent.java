package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceCancelledEvent extends DomainEvent {

  private final UUID invoiceId;
  private final String invoiceNumber;

  public InvoiceCancelledEvent(UUID tenantId, UUID invoiceId, String invoiceNumber) {
    super(tenantId, "INVOICE_CANCELLED");
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
  }
}
