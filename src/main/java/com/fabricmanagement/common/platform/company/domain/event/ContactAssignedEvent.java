package com.fabricmanagement.common.platform.company.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Fired when a contact is assigned to a company. Communication module may listen (e.g. send code).
 */
@Getter
public class ContactAssignedEvent extends DomainEvent {

  private final UUID companyId;
  private final UUID contactId;
  private final String ownerType;

  public ContactAssignedEvent(UUID tenantId, UUID companyId, UUID contactId) {
    super(tenantId, "CONTACT_ASSIGNED");
    this.companyId = companyId;
    this.contactId = contactId;
    this.ownerType = "COMPANY";
  }
}
