package com.fabricmanagement.company.infrastructure.integration.contact;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Basic contact DTO for contact-service integration.
 * This is a simplified version for basic contact operations.
 */
@Data
@Builder
public class ContactDto {
    private UUID id;
    private UUID companyId;
    private String contactType;
    private String status;
    
    // Basic contact information
    private String contactPersonName;
    private String contactPersonTitle;
    private String email;
    private String phoneNumber;
    private String address;
    
    // Additional info
    private String website;
    private String notes;
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

