package com.fabricmanagement.shared.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base Domain Event
 * 
 * All domain events should extend this class to ensure consistency
 * and proper event handling across the system.
 */
@Getter
@NoArgsConstructor
@ToString
public abstract class DomainEvent {

    @JsonProperty("eventId")
    private final UUID eventId = UUID.randomUUID();

    @JsonProperty("occurredAt")
    private final LocalDateTime occurredAt = LocalDateTime.now();

    @JsonProperty("eventVersion")
    private final String eventVersion = "1.0";

    /**
     * Get the type of this event
     * @return event type string
     */
    public abstract String getEventType();

    /**
     * Get the aggregate ID that this event belongs to
     * @return aggregate ID as string
     */
    public abstract String getAggregateId();

    /**
     * Get the tenant ID for multi-tenancy support
     * @return tenant ID, can be null for system events
     */
    public String getTenantId() {
        return null; // Override in specific events if needed
    }

    /**
     * Get event metadata
     * @return metadata map, can be null
     */
    public java.util.Map<String, Object> getMetadata() {
        return null; // Override in specific events if needed
    }
}
