package com.fabricmanagement.common.platform.company.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** Fired when an address is assigned to a company. */
@Getter
public class AddressAssignedEvent extends DomainEvent {

  private final UUID companyId;
  private final UUID addressId;
  private final String ownerType;

  public AddressAssignedEvent(UUID tenantId, UUID companyId, UUID addressId) {
    super(tenantId, "ADDRESS_ASSIGNED");
    this.companyId = companyId;
    this.addressId = addressId;
    this.ownerType = "COMPANY";
  }
}
