package com.fabricmanagement.company.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contact DTO for Feign Client
 * 
 * Simplified DTO for inter-service communication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore unknown fields from Contact Service
public class ContactDto {
    
    private UUID id;
    private UUID ownerId;
    private String ownerType;
    private String contactValue;
    private String contactType;
    private boolean isPrimary;
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

