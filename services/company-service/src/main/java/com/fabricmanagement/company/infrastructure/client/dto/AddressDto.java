package com.fabricmanagement.company.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Address DTO for Feign Client (Company Service)
 * 
 * Simplified DTO for inter-service communication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddressDto {
    
    private UUID id;
    private UUID contactId;
    private String ownerId;
    private String ownerType;
    
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String district;
    private String postalCode;
    private String country;
    
    private String addressType;
    private boolean isPrimary;
    private boolean isVerified;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

