package com.fabricmanagement.contact.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Value object for contact address.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactAddress {
    
    private UUID id;
    private String address;
    private String fullAddress;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private boolean isPrimary;
    private String type; // WORK, HOME, etc.
}
