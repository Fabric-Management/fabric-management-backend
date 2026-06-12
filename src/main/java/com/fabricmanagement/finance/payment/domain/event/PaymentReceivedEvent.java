package com.fabricmanagement.finance.payment.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import java.util.UUID;
import lombok.Getter;

@Getter
public class PaymentReceivedEvent extends DomainEvent {

  private final UUID paymentId;
  private final String paymentNumber;
  private final UUID tradingPartnerId;
  private final PaymentDirection direction;
  private final Money amount;
  private final String currency;

  public PaymentReceivedEvent(
      UUID tenantId,
      UUID paymentId,
      String paymentNumber,
      UUID tradingPartnerId,
      PaymentDirection direction,
      Money amount,
      String currency) {
    super(tenantId, "PAYMENT_RECEIVED");
    this.paymentId = paymentId;
    this.paymentNumber = paymentNumber;
    this.tradingPartnerId = tradingPartnerId;
    this.direction = direction;
    this.amount = amount;
    this.currency = currency;
  }
}
