package com.fabricmanagement.common.platform.organization.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.platform.organization.domain.OrganizationType;
import java.util.UUID;
import lombok.Getter;

/**
 * Domain event published when an organization is created.
 *
 * <p>Listeners: Audit, Analytics modules
 */
@Getter
public class OrganizationCreatedEvent extends DomainEvent {

  private final UUID organizationId;
  private final String name;
  private final OrganizationType organizationType;

  public OrganizationCreatedEvent(
      UUID tenantId, UUID organizationId, String name, OrganizationType organizationType) {
    super(tenantId, "ORGANIZATION_CREATED");
    this.organizationId = organizationId;
    this.name = name;
    this.organizationType = organizationType;
  }
}
