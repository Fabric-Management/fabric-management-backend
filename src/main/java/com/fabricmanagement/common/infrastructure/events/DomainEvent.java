package com.fabricmanagement.common.infrastructure.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.slf4j.MDC;

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

  @JsonProperty("eventId")
  private UUID eventId;

  @JsonProperty("tenantId")
  private UUID tenantId;

  @JsonProperty("eventType")
  private String eventType;

  @JsonProperty("occurredAt")
  private Instant occurredAt;

  @JsonProperty("correlationId")
  private String correlationId;

  protected DomainEvent(UUID tenantId, String eventType) {
    this(UUID.randomUUID(), tenantId, eventType, Instant.now(), null);
  }

  @JsonCreator
  protected DomainEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(eventType, "eventType must not be null");
    this.eventId = eventId != null ? eventId : UUID.randomUUID();
    this.tenantId = tenantId;
    this.eventType = eventType;
    this.occurredAt = occurredAt != null ? occurredAt : Instant.now();

    // Capture traceId as correlationId if present, otherwise fallback to eventId
    String traceId = MDC.get("traceId");
    this.correlationId =
        correlationId != null
            ? correlationId
            : (traceId != null && !traceId.isBlank()) ? traceId : this.eventId.toString();
  }

  @Override
  public String toString() {
    return String.format(
        "%s[id=%s, tenant=%s, type=%s, occurredAt=%s]",
        getClass().getSimpleName(), eventId, tenantId, eventType, occurredAt);
  }
}
