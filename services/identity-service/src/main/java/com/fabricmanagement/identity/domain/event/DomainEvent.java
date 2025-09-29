package com.fabricmanagement.identity.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base domain event for Identity Service.
 * Single Responsibility: Event representation only
 */
@Getter
@AllArgsConstructor
public abstract class DomainEvent {
    
    private final UUID eventId;
    private final String eventType;
    private final LocalDateTime timestamp;
    private final String userId;
    
    protected DomainEvent(String eventType, String userId) {
        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
        this.userId = userId;
    }
}