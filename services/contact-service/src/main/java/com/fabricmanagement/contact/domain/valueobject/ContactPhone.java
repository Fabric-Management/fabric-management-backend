package com.fabricmanagement.contact.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Value object for contact phone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactPhone {
    
    private UUID id;
    private String phoneNumber;
    private String fullPhoneNumber;
    private boolean isPrimary;
    private String type; // WORK, PERSONAL, MOBILE, etc.
}
