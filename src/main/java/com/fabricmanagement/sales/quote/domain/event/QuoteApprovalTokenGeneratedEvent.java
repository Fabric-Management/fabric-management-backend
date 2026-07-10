package com.fabricmanagement.sales.quote.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Event published after a customer approval token is generated for a quote. */
@Getter
public class QuoteApprovalTokenGeneratedEvent extends DomainEvent {

  private final UUID quoteId;
  private final String quoteNumber;
  private final String token;
  private final String customerEmail;
  private final QuoteApprovalChannel channel;
  private final String localeLanguageTag;

  @JsonCreator
  public QuoteApprovalTokenGeneratedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("quoteId") UUID quoteId,
      @JsonProperty("quoteNumber") String quoteNumber,
      @JsonProperty("token") String token,
      @JsonProperty("customerEmail") String customerEmail,
      @JsonProperty("channel") QuoteApprovalChannel channel,
      @JsonProperty("localeLanguageTag") String localeLanguageTag) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "QUOTE_APPROVAL_TOKEN_GENERATED",
        occurredAt,
        correlationId);
    this.quoteId = quoteId;
    this.quoteNumber = quoteNumber;
    this.token = token;
    this.customerEmail = customerEmail;
    this.channel = channel;
    this.localeLanguageTag = localeLanguageTag;
  }

  public QuoteApprovalTokenGeneratedEvent(
      UUID tenantId,
      UUID quoteId,
      String quoteNumber,
      String token,
      String customerEmail,
      QuoteApprovalChannel channel,
      String localeLanguageTag) {
    super(tenantId, "QUOTE_APPROVAL_TOKEN_GENERATED");
    this.quoteId = quoteId;
    this.quoteNumber = quoteNumber;
    this.token = token;
    this.customerEmail = customerEmail;
    this.channel = channel;
    this.localeLanguageTag = localeLanguageTag;
  }
}
