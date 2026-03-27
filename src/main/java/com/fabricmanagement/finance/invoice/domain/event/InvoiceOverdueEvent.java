package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceOverdueEvent extends DomainEvent {

  private final UUID invoiceId;
  private final String invoiceNumber;
  private final UUID tradingPartnerId;
  private final long daysOverdue;

  public InvoiceOverdueEvent(
      UUID tenantId,
      UUID invoiceId,
      String invoiceNumber,
      UUID tradingPartnerId,
      long daysOverdue) {
    super(tenantId, "INVOICE_OVERDUE");
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.tradingPartnerId = tradingPartnerId;
    this.daysOverdue = daysOverdue;
  }
}
