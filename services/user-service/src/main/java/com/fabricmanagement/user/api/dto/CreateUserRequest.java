package com.fabricmanagement.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Create User Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    
    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;
    
    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
    
    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;
    
    private String role;
    
    private Map<String, Object> preferences;
    
    private Map<String, Object> settings;
}

