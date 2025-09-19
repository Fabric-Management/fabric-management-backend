package com.fabricmanagement.contact.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Value object for contact email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactEmail {
    
    private UUID id;
    private String email;
    private boolean isPrimary;
    private String type; // WORK, PERSONAL, etc.
}
