package com.fabricmanagement.common.infrastructure.events;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Base class for all domain events in the system.
 *
 * <p>Domain events represent something significant that happened in the domain. They are used for
 * loose coupling between modules and eventual consistency.
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * public class ProductCreatedEvent extends DomainEvent {
 *     private final UUID productId;
 *     private final String productName;
 *
 *     public ProductCreatedEvent(UUID tenantId, UUID productId, String productName) {
 *         super(tenantId, "PRODUCT_CREATED");
 *         this.productId = productId;
 *         this.productName = productName;
 *     }
 * }
 * }</pre>
 *
 * <h2>Event Flow:</h2>
 *
 * <ol>
 *   <li>Domain service publishes event via ApplicationEventPublisher
 *   <li>Spring Modulith stores event in event_publication table
 *   <li>Event listeners receive event (same transaction or async)
 *   <li>Event marked as completed after successful processing
 * </ol>
 */
@Getter
public abstract class DomainEvent {

  private final UUID eventId;
  private final UUID tenantId;
  private final String eventType;
  private final Instant occurredAt;

  protected DomainEvent(UUID tenantId, String eventType) {
    this.eventId = UUID.randomUUID();
    this.tenantId = tenantId;
    this.eventType = eventType;
    this.occurredAt = Instant.now();
  }

  @Override
  public String toString() {
    return String.format(
        "%s[id=%s, tenant=%s, type=%s, occurredAt=%s]",
        getClass().getSimpleName(), eventId, tenantId, eventType, occurredAt);
  }
}
