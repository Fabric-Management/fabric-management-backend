package com.fabricmanagement.sales.quote.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

@Getter
public class QuoteSendRequestRejectedEvent extends DomainEvent {

  private final UUID quoteSendRequestId;
  private final UUID quoteId;
  private final String quoteNumber;
  private final UUID requesterId;
  private final String decisionNote;

  public QuoteSendRequestRejectedEvent(
      UUID tenantId,
      UUID quoteSendRequestId,
      UUID quoteId,
      String quoteNumber,
      UUID requesterId,
      String decisionNote) {
    super(tenantId, "QUOTE_SEND_REQUEST_REJECTED");
    this.quoteSendRequestId = quoteSendRequestId;
    this.quoteId = quoteId;
    this.quoteNumber = quoteNumber;
    this.requesterId = requesterId;
    this.decisionNote = decisionNote;
  }
}
