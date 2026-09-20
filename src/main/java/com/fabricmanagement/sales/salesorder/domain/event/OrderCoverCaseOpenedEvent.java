package com.fabricmanagement.sales.salesorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public class OrderCoverCaseOpenedEvent extends DomainEvent {
  private final UUID caseId;
  private final UUID salesOrderId;
  private final String orderNumber;
  private final LocalDate deadline;
  private final Set<UUID> unresolvedLineIds;

  public OrderCoverCaseOpenedEvent(
      UUID tenantId,
      UUID caseId,
      UUID salesOrderId,
      String orderNumber,
      LocalDate deadline,
      Set<UUID> unresolvedLineIds) {
    super(tenantId, "OrderCoverCaseOpened");
    this.caseId = caseId;
    this.salesOrderId = salesOrderId;
    this.orderNumber = orderNumber;
    this.deadline = deadline;
    this.unresolvedLineIds = Set.copyOf(unresolvedLineIds);
  }

  public UUID getCaseId() {
    return caseId;
  }

  public UUID getSalesOrderId() {
    return salesOrderId;
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public LocalDate getDeadline() {
    return deadline;
  }

  public Set<UUID> getUnresolvedLineIds() {
    return unresolvedLineIds;
  }

  @JsonCreator
  public OrderCoverCaseOpenedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("caseId") UUID caseId,
      @JsonProperty("salesOrderId") UUID salesOrderId,
      @JsonProperty("orderNumber") String orderNumber,
      @JsonProperty("deadline") LocalDate deadline,
      @JsonProperty("unresolvedLineIds") Set<UUID> unresolvedLineIds) {
    super(
        eventId,
        tenantId,
        eventType == null ? "OrderCoverCaseOpened" : eventType,
        occurredAt,
        correlationId);
    this.caseId = caseId;
    this.salesOrderId = salesOrderId;
    this.orderNumber = orderNumber;
    this.deadline = deadline;
    this.unresolvedLineIds = unresolvedLineIds == null ? Set.of() : Set.copyOf(unresolvedLineIds);
  }
}
