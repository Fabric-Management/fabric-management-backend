package com.fabricmanagement.human.core.employee.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Getter
public class EmployeeTerminatedEvent extends DomainEvent {
  private final UUID userId;
  private final LocalDate terminationDate;

  public EmployeeTerminatedEvent(UUID tenantId, UUID userId, LocalDate terminationDate) {
    super(tenantId, "EMPLOYEE_TERMINATED");
    this.userId = userId;
    this.terminationDate = terminationDate;
  }
}
