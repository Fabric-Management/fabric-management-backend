package com.fabricmanagement.production.execution.inventory.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
public class InventoryTransactionCreatedEvent extends DomainEvent {

  private final UUID transactionId;
  private final UUID batchId;
  private final InventoryTransactionType transactionType;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID locationId;
  private final Instant transactionDate;

  @JsonCreator
  public InventoryTransactionCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("transactionId") UUID transactionId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("transactionType") InventoryTransactionType transactionType,
      @JsonProperty("quantity") BigDecimal quantity,
      @JsonProperty("unit") String unit,
      @JsonProperty("locationId") UUID locationId,
      @JsonProperty("transactionDate") Instant transactionDate) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "INVENTORY_TRANSACTION_CREATED",
        occurredAt,
        correlationId);
    this.transactionId = transactionId;
    this.batchId = batchId;
    this.transactionType = transactionType;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
    this.transactionDate = transactionDate;
  }

  @Builder
  public InventoryTransactionCreatedEvent(
      UUID tenantId,
      UUID transactionId,
      UUID batchId,
      InventoryTransactionType transactionType,
      BigDecimal quantity,
      String unit,
      UUID locationId,
      Instant transactionDate) {
    super(tenantId, "INVENTORY_TRANSACTION_CREATED");
    this.transactionId = transactionId;
    this.batchId = batchId;
    this.transactionType = transactionType;
    this.quantity = quantity;
    this.unit = unit;
    this.locationId = locationId;
    this.transactionDate = transactionDate;
  }
}
