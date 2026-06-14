package com.fabricmanagement.finance.payment.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public class PaymentVoidedEvent extends DomainEvent {

  private final UUID paymentId;
  private final String paymentNumber;
  private final List<UUID> affectedInvoiceIds;

  public PaymentVoidedEvent(
      UUID tenantId, UUID paymentId, String paymentNumber, List<UUID> affectedInvoiceIds) {
    super(tenantId, "PAYMENT_VOIDED");
    this.paymentId = paymentId;
    this.paymentNumber = paymentNumber;
    this.affectedInvoiceIds = affectedInvoiceIds;
  }
}
