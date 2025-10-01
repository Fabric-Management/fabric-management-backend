package com.fabricmanagement.user.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create Contact DTO for Contact Service communication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContactDto {
    
    private String ownerId;
    private String ownerType;
    private String contactValue;
    private String contactType;
    private boolean isPrimary;
    private boolean autoVerified;
}
