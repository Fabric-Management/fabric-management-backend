package com.fabricmanagement.contact.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

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
    private String parentContactId;  // For PHONE_EXTENSION
    private boolean isVerified;
    private boolean isPrimary;
    private String verificationCode;  // Only returned during creation
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

