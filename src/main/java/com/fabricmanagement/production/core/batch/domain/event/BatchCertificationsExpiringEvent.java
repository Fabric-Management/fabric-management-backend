package com.fabricmanagement.production.core.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * Published by the daily check when batch certifications of a tenant expire on or before {@code
 * thresholdDate}.
 */
@Getter
public class BatchCertificationsExpiringEvent extends DomainEvent {

  private final int count;
  private final LocalDate thresholdDate;

  public BatchCertificationsExpiringEvent(UUID tenantId, int count, LocalDate thresholdDate) {
    super(tenantId, "BATCH_CERTIFICATIONS_EXPIRING");
    this.count = count;
    this.thresholdDate = thresholdDate;
  }

  @JsonCreator
  public BatchCertificationsExpiringEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("count") int count,
      @JsonProperty("thresholdDate") LocalDate thresholdDate) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_CERTIFICATIONS_EXPIRING",
        occurredAt,
        correlationId);
    this.count = count;
    this.thresholdDate = thresholdDate;
  }
}
