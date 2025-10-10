package com.fabricmanagement.user.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * User Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    
    private UUID id;
    private UUID tenantId;
    private String firstName;
    private String lastName;
    private String displayName;
    private String email;
    private String phone;
    private String status;
    private String registrationType;
    private String role;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Map<String, Object> preferences;
    private Map<String, Object> settings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}

