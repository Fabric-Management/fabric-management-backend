package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class PaymentRecordedEvent extends DomainEvent {

  private final UUID invoiceId;
  private final String invoiceNumber;
  private final BigDecimal paymentAmount;
  private final BigDecimal totalPaid;
  private final BigDecimal amountDue;
  private final boolean fullyPaid;

  public PaymentRecordedEvent(
      UUID tenantId,
      UUID invoiceId,
      String invoiceNumber,
      BigDecimal paymentAmount,
      BigDecimal totalPaid,
      BigDecimal amountDue,
      boolean fullyPaid) {
    super(tenantId, "PAYMENT_RECORDED");
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.paymentAmount = paymentAmount;
    this.totalPaid = totalPaid;
    this.amountDue = amountDue;
    this.fullyPaid = fullyPaid;
  }
}
