package com.fabricmanagement.platform.organization.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
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
}
