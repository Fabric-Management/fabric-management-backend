package com.fabricmanagement.common.platform.company.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a new company is created.
 *
 * <p>Listeners: All modules, Audit, Analytics
 */
@Getter
public class CompanyCreatedEvent extends DomainEvent {

  private final UUID companyId;
  private final String companyName;
  private final String companyType;

  public CompanyCreatedEvent(
      UUID tenantId, UUID companyId, String companyName, String companyType) {
    super(tenantId, "COMPANY_CREATED");
    this.companyId = companyId;
    this.companyName = companyName;
    this.companyType = companyType;
  }
}
