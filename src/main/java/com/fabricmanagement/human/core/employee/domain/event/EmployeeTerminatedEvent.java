package com.fabricmanagement.human.core.employee.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Getter
public class EmployeeTerminatedEvent extends DomainEvent {
  private final UUID userId;
  private final LocalDate terminationDate;

  @JsonCreator
  public EmployeeTerminatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("terminationDate") LocalDate terminationDate) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "EMPLOYEE_TERMINATED",
        occurredAt,
        correlationId);
    this.userId = userId;
    this.terminationDate = terminationDate;
  }

  public EmployeeTerminatedEvent(UUID tenantId, UUID userId, LocalDate terminationDate) {
    super(tenantId, "EMPLOYEE_TERMINATED");
    this.userId = userId;
    this.terminationDate = terminationDate;
  }
}
