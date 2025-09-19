package com.fabricmanagement.identity.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdatedEvent implements DomainEvent {
    private UUID aggregateId;
    private String username;
    private Map<String, String> updatedFields; // field name -> new value
    private Map<String, String> previousValues; // field name -> old value
    private String updatedBy; // USER, ADMIN, etc.
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Override
    public String getEventType() {
        return "UserProfileUpdated";
    }
}