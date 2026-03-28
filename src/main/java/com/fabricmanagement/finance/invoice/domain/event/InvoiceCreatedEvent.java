package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InvoiceCreatedEvent extends DomainEvent {

  private final UUID invoiceId;
  private final UUID tradingPartnerId;
  private final String invoiceNumber;
  private final String invoiceType;
  private final BigDecimal totalAmount;
  private final String currency;

  public InvoiceCreatedEvent(
      UUID tenantId,
      UUID invoiceId,
      UUID tradingPartnerId,
      String invoiceNumber,
      String invoiceType,
      BigDecimal totalAmount,
      String currency) {
    super(tenantId, "INVOICE_CREATED");
    this.invoiceId = invoiceId;
    this.tradingPartnerId = tradingPartnerId;
    this.invoiceNumber = invoiceNumber;
    this.invoiceType = invoiceType;
    this.totalAmount = totalAmount;
    this.currency = currency;
  }
}
