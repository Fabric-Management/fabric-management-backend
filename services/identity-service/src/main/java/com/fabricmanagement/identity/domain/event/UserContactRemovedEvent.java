package com.fabricmanagement.identity.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContactRemovedEvent implements DomainEvent {
    private UUID aggregateId;
    private String contactType; // EMAIL, PHONE, etc.
    private String contactValue;
    private String removalReason; // USER_REQUEST, ADMIN_ACTION, SECURITY_VIOLATION, etc.
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "UserContactRemoved";
    }
}