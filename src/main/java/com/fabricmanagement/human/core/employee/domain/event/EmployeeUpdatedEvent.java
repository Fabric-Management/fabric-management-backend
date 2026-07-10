package com.fabricmanagement.human.core.employee.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
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

  @JsonCreator
  public EmployeeUpdatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "EMPLOYEE_UPDATED",
        occurredAt,
        correlationId);
    this.userId = userId;
  }

  public EmployeeUpdatedEvent(UUID tenantId, UUID userId) {
    super(tenantId, "EMPLOYEE_UPDATED");
    this.userId = userId;
  }
}
