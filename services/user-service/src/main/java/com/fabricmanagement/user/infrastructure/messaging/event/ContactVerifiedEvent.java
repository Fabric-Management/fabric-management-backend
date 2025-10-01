package com.fabricmanagement.user.infrastructure.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contact Verified Event
 * 
 * Published when a contact is verified
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactVerifiedEvent {
    
    private UUID contactId;
    private String ownerId;
    private String ownerType;
    private String contactValue;
    private String contactType;
    private LocalDateTime verifiedAt;
}
