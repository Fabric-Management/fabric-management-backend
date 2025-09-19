package com.fabricmanagement.contact.infrastructure.integration.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for user information from identity-service.
 * Used for cross-service communication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    
    private UUID id;
    private UUID tenantId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    
    // Contact information
    private String primaryEmail;
    private String primaryPhone;
    
    // Security information
    private boolean twoFactorEnabled;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
}