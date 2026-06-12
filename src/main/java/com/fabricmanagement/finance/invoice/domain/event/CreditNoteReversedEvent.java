package com.fabricmanagement.finance.invoice.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.util.Money;
import java.util.UUID;
import lombok.Getter;

@Getter
public class CreditNoteReversedEvent extends DomainEvent {
  private final UUID creditNoteId;
  private final UUID targetInvoiceId;
  private final Money amount;
  private final String targetNewPaymentStatus;

  public CreditNoteReversedEvent(
      UUID tenantId,
      UUID creditNoteId,
      UUID targetInvoiceId,
      Money amount,
      String targetNewPaymentStatus) {
    super(tenantId, "CREDIT_NOTE_REVERSED");
    this.creditNoteId = creditNoteId;
    this.targetInvoiceId = targetInvoiceId;
    this.amount = amount;
    this.targetNewPaymentStatus = targetNewPaymentStatus;
  }
}
