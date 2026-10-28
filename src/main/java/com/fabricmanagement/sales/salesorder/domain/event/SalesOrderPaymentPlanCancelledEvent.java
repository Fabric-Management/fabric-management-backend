package com.fabricmanagement.sales.salesorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class SalesOrderPaymentPlanCancelledEvent extends DomainEvent {
  private final UUID salesOrderId;
  private final UUID planId;

  public SalesOrderPaymentPlanCancelledEvent(UUID tenantId, UUID order, UUID plan) {
    super(tenantId, "SalesOrderPaymentPlanCancelled");
    this.salesOrderId = order;
    this.planId = plan;
  }

  /** Rebuilds a stored event with its original identity, type, time and correlation. */
  @JsonCreator
  public SalesOrderPaymentPlanCancelledEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("salesOrderId") UUID salesOrderId,
      @JsonProperty("planId") UUID planId) {
    super(
        eventId,
        tenantId,
        eventType == null ? "SalesOrderPaymentPlanCancelled" : eventType,
        occurredAt,
        correlationId);
    this.salesOrderId = salesOrderId;
    this.planId = planId;
  }
}
