package com.fabricmanagement.identity.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for domain events.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class DomainEvent {
    private UUID eventId;
    private LocalDateTime occurredAt;
    private String eventType;

    protected DomainEvent(String eventType) {
        this.eventId = UUID.randomUUID();
        this.occurredAt = LocalDateTime.now();
        this.eventType = eventType;
    }
}