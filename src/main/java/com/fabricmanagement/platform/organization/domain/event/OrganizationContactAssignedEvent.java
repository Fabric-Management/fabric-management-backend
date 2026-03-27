package com.fabricmanagement.platform.organization.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** Fired when a contact is assigned to an organization. */
@Getter
public class OrganizationContactAssignedEvent extends DomainEvent {

  private final UUID organizationId;
  private final UUID contactId;

  public OrganizationContactAssignedEvent(UUID tenantId, UUID organizationId, UUID contactId) {
    super(tenantId, "ORGANIZATION_CONTACT_ASSIGNED");
    this.organizationId = organizationId;
    this.contactId = contactId;
  }
}
