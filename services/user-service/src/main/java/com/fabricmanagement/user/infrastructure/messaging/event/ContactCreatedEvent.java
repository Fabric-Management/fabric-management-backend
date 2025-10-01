package com.fabricmanagement.user.infrastructure.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Contact Created Event
 * 
 * Published when a new contact is created
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactCreatedEvent {
    
    private UUID contactId;
    private String ownerId;
    private String ownerType;
    private String contactValue;
    private String contactType;
}
