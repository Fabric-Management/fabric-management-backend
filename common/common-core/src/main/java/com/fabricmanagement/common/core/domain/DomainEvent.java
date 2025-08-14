package com.fabricmanagement.common.core.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DomainEvent {
    UUID getEventId();
    LocalDateTime getOccurredAt();
    String getEventType();
}