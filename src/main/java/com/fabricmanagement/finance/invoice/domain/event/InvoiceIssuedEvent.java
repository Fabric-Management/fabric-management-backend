package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceIssuedEvent extends DomainEvent {

  private final UUID invoiceId;
  private final String invoiceNumber;
  private final BigDecimal totalAmount;

  public InvoiceIssuedEvent(
      UUID tenantId, UUID invoiceId, String invoiceNumber, BigDecimal totalAmount) {
    super(tenantId, "INVOICE_ISSUED");
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.totalAmount = totalAmount;
  }
}
