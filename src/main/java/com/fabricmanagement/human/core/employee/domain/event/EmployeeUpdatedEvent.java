package com.fabricmanagement.human.core.employee.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when an Employee record is created or updated.
 *
 * <p>Used by UserCacheInvalidationService to evict user caches that now carry Employee data.
 */
@Getter
public class EmployeeUpdatedEvent extends DomainEvent {

  private final UUID userId;

  public EmployeeUpdatedEvent(UUID tenantId, UUID userId) {
    super(tenantId, "EMPLOYEE_UPDATED");
    this.userId = userId;
  }
}
