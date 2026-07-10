package com.fabricmanagement.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Fired when an address is assigned to a user. */
@Getter
public class AddressAssignedEvent extends DomainEvent {

  private final UUID userId;
  private final UUID addressId;
  private final String ownerType;

  public AddressAssignedEvent(UUID tenantId, UUID userId, UUID addressId) {
    super(tenantId, "ADDRESS_ASSIGNED");
    this.userId = userId;
    this.addressId = addressId;
    this.ownerType = "USER";
  }

  @JsonCreator
  public AddressAssignedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("addressId") UUID addressId,
      @JsonProperty("ownerType") String ownerType) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "ADDRESS_ASSIGNED",
        occurredAt,
        correlationId);
    this.userId = userId;
    this.addressId = addressId;
    this.ownerType = ownerType != null ? ownerType : "USER";
  }
}
