package com.fabricmanagement.sales.salesorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class SalesOrderPaymentPlanRevisedEvent extends DomainEvent {
  private final UUID salesOrderId;
  private final UUID planId;
  private final UUID revisionId;
  private final UUID supersededRevisionId;

  public SalesOrderPaymentPlanRevisedEvent(
      UUID tenantId, UUID order, UUID plan, UUID revision, UUID previous) {
    super(tenantId, "SalesOrderPaymentPlanRevised");
    this.salesOrderId = order;
    this.planId = plan;
    this.revisionId = revision;
    this.supersededRevisionId = previous;
  }

  /** Rebuilds a stored event with its original identity, type, time and correlation. */
  @JsonCreator
  public SalesOrderPaymentPlanRevisedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("salesOrderId") UUID salesOrderId,
      @JsonProperty("planId") UUID planId,
      @JsonProperty("revisionId") UUID revisionId,
      @JsonProperty("supersededRevisionId") UUID supersededRevisionId) {
    super(
        eventId,
        tenantId,
        eventType == null ? "SalesOrderPaymentPlanRevised" : eventType,
        occurredAt,
        correlationId);
    this.salesOrderId = salesOrderId;
    this.planId = planId;
    this.revisionId = revisionId;
    this.supersededRevisionId = supersededRevisionId;
  }
}
