package com.fabricmanagement.platform.communication.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A contact point no longer reaches what it reached before: its value now names another address (or
 * number), its type changed, or it was deleted. Published inside the transaction that made the
 * change, so a synchronous listener can end whatever rested on the earlier address in the same
 * commit. A change of letter case in an e-mail address is not a change.
 */
@Getter
public class ContactPointChangedEvent extends DomainEvent {

  public static final String ADDRESS_CHANGED = "ADDRESS_CHANGED";
  public static final String DELETED = "DELETED";

  private final UUID contactId;
  private final String change;

  public ContactPointChangedEvent(UUID tenantId, UUID contactId, String change) {
    super(tenantId, "CONTACT_POINT_CHANGED");
    this.contactId = contactId;
    this.change = change;
  }

  @JsonCreator
  public ContactPointChangedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("contactId") UUID contactId,
      @JsonProperty("change") String change) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "CONTACT_POINT_CHANGED",
        occurredAt,
        correlationId);
    this.contactId = contactId;
    this.change = change;
  }
}
