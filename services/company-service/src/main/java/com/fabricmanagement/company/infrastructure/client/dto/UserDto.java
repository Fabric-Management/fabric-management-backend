package com.fabricmanagement.company.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User DTO from User Service
 * 
 * Note: This DTO mirrors the User entity structure from user-service.
 * No username field - authentication uses contact info (email/phone).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private UUID companyId;
    private LocalDateTime createdAt;
}

