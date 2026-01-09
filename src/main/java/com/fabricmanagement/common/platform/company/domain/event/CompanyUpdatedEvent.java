package com.fabricmanagement.common.platform.company.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when company information is updated.
 *
 * <p>Listeners: Audit, Analytics, Cache invalidation
 */
@Getter
public class CompanyUpdatedEvent extends DomainEvent {

  private final UUID companyId;
  private final String companyName;

  public CompanyUpdatedEvent(UUID tenantId, UUID companyId, String companyName) {
    super(tenantId, "COMPANY_UPDATED");
    this.companyId = companyId;
    this.companyName = companyName;
  }
}
