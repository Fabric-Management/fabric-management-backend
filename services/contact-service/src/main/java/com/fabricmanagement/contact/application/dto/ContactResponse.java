package com.fabricmanagement.contact.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contact Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponse {
    
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
