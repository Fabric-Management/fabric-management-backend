package com.fabricmanagement.user.domain.event;

import com.fabricmanagement.common.core.domain.DomainEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class UserActivatedEvent implements DomainEvent {

    private final UUID eventId = UUID.randomUUID();
    private final LocalDateTime occurredAt = LocalDateTime.now();
    private final UUID userId;

    @Override
    public String getEventType() {
        return "UserActivated";
    }
}