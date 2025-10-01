package com.fabricmanagement.user.infrastructure.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Contact Deleted Event
 * 
 * Published when a contact is deleted
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDeletedEvent {
    
    private UUID contactId;
    private String ownerId;
    private String ownerType;
}
