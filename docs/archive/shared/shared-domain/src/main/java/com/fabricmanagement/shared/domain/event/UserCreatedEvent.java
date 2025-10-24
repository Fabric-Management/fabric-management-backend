package com.fabricmanagement.shared.domain.event;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * User Created Event
 * 
 * Domain event for user creation
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ DOMAIN EVENT
 * ✅ UUID TYPE SAFETY
 */
@Getter
@Setter
@Builder
@ToString
public class UserCreatedEvent {
    
    private UUID eventId;
    private LocalDateTime occurredAt;
    private UUID userId;
    private UUID tenantId;
    private String contactValue;
    private String contactType;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String status;
    private String traceId;
    private String correlationId;
    private Map<String, Object> metadata;
    
    /**
     * Create UserCreatedEvent
     */
    public static UserCreatedEvent create(UUID userId, UUID tenantId, String contactValue, 
                                        String contactType, String firstName, String lastName, 
                                        String email, String phone, String status) {
        return UserCreatedEvent.builder()
            .eventId(UUID.randomUUID())
            .occurredAt(LocalDateTime.now())
            .userId(userId)
            .tenantId(tenantId)
            .contactValue(contactValue)
            .contactType(contactType)
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .phone(phone)
            .status(status)
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }
    
    /**
     * Create UserCreatedEvent with metadata
     */
    public static UserCreatedEvent create(UUID userId, UUID tenantId, String contactValue, 
                                        String contactType, String firstName, String lastName, 
                                        String email, String phone, String status, 
                                        Map<String, Object> metadata) {
        return UserCreatedEvent.builder()
            .eventId(UUID.randomUUID())
            .occurredAt(LocalDateTime.now())
            .userId(userId)
            .tenantId(tenantId)
            .contactValue(contactValue)
            .contactType(contactType)
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .phone(phone)
            .status(status)
            .traceId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .metadata(metadata)
            .build();
    }
}