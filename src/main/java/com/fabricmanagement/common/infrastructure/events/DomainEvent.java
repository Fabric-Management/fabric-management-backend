package com.fabricmanagement.common.infrastructure.events;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all domain events in the system.
 *
 * <p>Domain events represent something significant that happened in the domain.
 * They are used for loose coupling between modules and eventual consistency.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * public class MaterialCreatedEvent extends DomainEvent {
 *     private final UUID materialId;
 *     private final String materialName;
 *
 *     public MaterialCreatedEvent(UUID tenantId, UUID materialId, String materialName) {
 *         super(tenantId, "MATERIAL_CREATED");
 *         this.materialId = materialId;
 *         this.materialName = materialName;
 *     }
 * }
 * }</pre>
 *
 * <h2>Event Flow:</h2>
 * <ol>
 *   <li>Domain service publishes event via ApplicationEventPublisher</li>
 *   <li>Spring Modulith stores event in event_publication table</li>
 *   <li>Event listeners receive event (same transaction or async)</li>
 *   <li>Event marked as completed after successful processing</li>
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
        return String.format("%s[id=%s, tenant=%s, type=%s, occurredAt=%s]",
            getClass().getSimpleName(), eventId, tenantId, eventType, occurredAt);
    }
}

