package com.fabricmanagement.sales.quote.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class QuoteSendRequestedEvent extends DomainEvent {

  private final UUID quoteSendRequestId;
  private final UUID quoteId;
  private final String quoteNumber;
  private final UUID requestedBy;

  @JsonCreator
  public QuoteSendRequestedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("quoteSendRequestId") UUID quoteSendRequestId,
      @JsonProperty("quoteId") UUID quoteId,
      @JsonProperty("quoteNumber") String quoteNumber,
      @JsonProperty("requestedBy") UUID requestedBy) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "QUOTE_SEND_REQUESTED",
        occurredAt,
        correlationId);
    this.quoteSendRequestId = quoteSendRequestId;
    this.quoteId = quoteId;
    this.quoteNumber = quoteNumber;
    this.requestedBy = requestedBy;
  }

  public QuoteSendRequestedEvent(
      UUID tenantId, UUID quoteSendRequestId, UUID quoteId, String quoteNumber, UUID requestedBy) {
    super(tenantId, "QUOTE_SEND_REQUESTED");
    this.quoteSendRequestId = quoteSendRequestId;
    this.quoteId = quoteId;
    this.quoteNumber = quoteNumber;
    this.requestedBy = requestedBy;
  }
}
