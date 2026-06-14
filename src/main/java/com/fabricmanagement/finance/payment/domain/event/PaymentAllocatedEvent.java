package com.fabricmanagement.finance.payment.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.util.Money;
import java.util.UUID;
import lombok.Getter;

@Getter
public class PaymentAllocatedEvent extends DomainEvent {

  private final UUID paymentId;
  private final UUID invoiceId;
  private final Money allocationAmount;

  public PaymentAllocatedEvent(
      UUID tenantId, UUID paymentId, UUID invoiceId, Money allocationAmount) {
    super(tenantId, "PAYMENT_ALLOCATED");
    this.paymentId = paymentId;
    this.invoiceId = invoiceId;
    this.allocationAmount = allocationAmount;
  }
}
