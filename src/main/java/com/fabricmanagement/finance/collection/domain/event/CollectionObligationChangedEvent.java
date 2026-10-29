package com.fabricmanagement.finance.collection.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class CollectionObligationChangedEvent extends DomainEvent {
  private final UUID obligationId;

  public CollectionObligationChangedEvent(UUID tenant, UUID obligation) {
    super(tenant, "COLLECTION_OBLIGATION_CHANGED");
    obligationId = obligation;
  }

  @JsonCreator
  public CollectionObligationChangedEvent(
      @JsonProperty("eventId") UUID id,
      @JsonProperty("tenantId") UUID tenant,
      @JsonProperty("eventType") String type,
      @JsonProperty("occurredAt") Instant at,
      @JsonProperty("correlationId") String correlation,
      @JsonProperty("obligationId") UUID obligation) {
    super(id, tenant, type == null ? "COLLECTION_OBLIGATION_CHANGED" : type, at, correlation);
    obligationId = obligation;
  }
}
