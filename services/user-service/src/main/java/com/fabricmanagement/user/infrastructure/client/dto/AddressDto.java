package com.fabricmanagement.user.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Address DTO for Feign Client
 * 
 * Maps to Contact Service's AddressResponse
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
    
    // Address fields
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String district;
    private String postalCode;
    private String country;
    
    // Google Places (optional)
    private String googlePlaceId;
    private String formattedAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    
    // Metadata
    private String addressType;
    private boolean isPrimary;
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    
    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

