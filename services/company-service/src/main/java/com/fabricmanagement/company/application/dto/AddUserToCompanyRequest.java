package com.fabricmanagement.company.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Add User to Company Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddUserToCompanyRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    private String role;
    
    private String department;
    
    private String position;
}

