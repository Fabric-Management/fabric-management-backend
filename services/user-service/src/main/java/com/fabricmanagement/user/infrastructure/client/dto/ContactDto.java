package com.fabricmanagement.user.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contact DTO for Contact Service communication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDto {
    
    private UUID id;
    private String ownerId;
    private String ownerType;
    private String contactValue;
    private String contactType;
    private boolean isVerified;
    private boolean isPrimary;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
