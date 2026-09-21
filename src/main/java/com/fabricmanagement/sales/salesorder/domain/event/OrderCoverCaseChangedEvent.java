package com.fabricmanagement.sales.salesorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverCaseState;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Durable fact emitted whenever an order-cover case changes state or records a receipt. */
@Getter
public class OrderCoverCaseChangedEvent extends DomainEvent {
  private final UUID caseId;
  private final long revision;
  private final OrderCoverCaseState state;
  private final UUID resultId;

  public OrderCoverCaseChangedEvent(
      UUID tenantId, UUID caseId, long revision, OrderCoverCaseState state, UUID resultId) {
    super(tenantId, "ORDER_COVER_CASE_CHANGED");
    this.caseId = caseId;
    this.revision = revision;
    this.state = state;
    this.resultId = resultId;
  }

  @JsonCreator
  public OrderCoverCaseChangedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("caseId") UUID caseId,
      @JsonProperty("revision") long revision,
      @JsonProperty("state") OrderCoverCaseState state,
      @JsonProperty("resultId") UUID resultId) {
    super(
        eventId,
        tenantId,
        eventType == null ? "ORDER_COVER_CASE_CHANGED" : eventType,
        occurredAt,
        correlationId);
    this.caseId = caseId;
    this.revision = revision;
    this.state = state;
    this.resultId = resultId;
  }
}
