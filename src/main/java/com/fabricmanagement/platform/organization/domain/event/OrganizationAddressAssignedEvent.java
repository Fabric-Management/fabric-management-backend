package com.fabricmanagement.platform.organization.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Fired when an address is assigned to an organization. */
@Getter
public class OrganizationAddressAssignedEvent extends DomainEvent {

  private final UUID organizationId;
  private final UUID addressId;

  public OrganizationAddressAssignedEvent(UUID tenantId, UUID organizationId, UUID addressId) {
    super(tenantId, "ORGANIZATION_ADDRESS_ASSIGNED");
    this.organizationId = organizationId;
    this.addressId = addressId;
  }

  @JsonCreator
  public OrganizationAddressAssignedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("organizationId") UUID organizationId,
      @JsonProperty("addressId") UUID addressId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "ORGANIZATION_ADDRESS_ASSIGNED",
        occurredAt,
        correlationId);
    this.organizationId = organizationId;
    this.addressId = addressId;
  }
}
