package com.fabricmanagement.company.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create Contact DTO for Company Service
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

