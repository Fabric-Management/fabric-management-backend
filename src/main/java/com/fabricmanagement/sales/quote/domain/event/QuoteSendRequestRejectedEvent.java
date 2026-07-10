package com.fabricmanagement.sales.quote.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class QuoteSendRequestRejectedEvent extends DomainEvent {

  private final UUID quoteSendRequestId;
  private final UUID quoteId;
  private final String quoteNumber;
  private final UUID requesterId;
  private final String decisionNote;

  @JsonCreator
  public QuoteSendRequestRejectedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("quoteSendRequestId") UUID quoteSendRequestId,
      @JsonProperty("quoteId") UUID quoteId,
      @JsonProperty("quoteNumber") String quoteNumber,
      @JsonProperty("requesterId") UUID requesterId,
      @JsonProperty("decisionNote") String decisionNote) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "QUOTE_SEND_REQUEST_REJECTED",
        occurredAt,
        correlationId);
    this.quoteSendRequestId = quoteSendRequestId;
    this.quoteId = quoteId;
    this.quoteNumber = quoteNumber;
    this.requesterId = requesterId;
    this.decisionNote = decisionNote;
  }

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
