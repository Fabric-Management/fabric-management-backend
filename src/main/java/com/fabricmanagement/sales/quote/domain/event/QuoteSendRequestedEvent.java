package com.fabricmanagement.sales.quote.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

@Getter
public class QuoteSendRequestedEvent extends DomainEvent {

  private final UUID quoteSendRequestId;
  private final UUID quoteId;
  private final String quoteNumber;
  private final UUID requestedBy;

  public QuoteSendRequestedEvent(
      UUID tenantId, UUID quoteSendRequestId, UUID quoteId, String quoteNumber, UUID requestedBy) {
    super(tenantId, "QUOTE_SEND_REQUESTED");
    this.quoteSendRequestId = quoteSendRequestId;
    this.quoteId = quoteId;
    this.quoteNumber = quoteNumber;
    this.requestedBy = requestedBy;
  }
}
