package com.fabricmanagement.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
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
}
