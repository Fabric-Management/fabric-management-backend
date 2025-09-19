package com.fabricmanagement.identity.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base interface for domain events.
 */
public interface DomainEvent {
    UUID getAggregateId();
    LocalDateTime getOccurredAt();
    String getEventType();
}