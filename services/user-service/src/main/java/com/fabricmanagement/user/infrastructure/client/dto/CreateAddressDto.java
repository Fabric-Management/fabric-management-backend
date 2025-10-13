package com.fabricmanagement.user.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create Address DTO for Feign Client
 * 
 * Maps to Contact Service's CreateAddressRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAddressDto {
    
    private String ownerId;
    private String ownerType;
    
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String district;
    private String postalCode;
    private String country;
    
    private String addressType;  // HOME, WORK, BILLING, SHIPPING
    private Boolean isPrimary;
    
    // Google Places (optional)
    private String googlePlaceId;
}

