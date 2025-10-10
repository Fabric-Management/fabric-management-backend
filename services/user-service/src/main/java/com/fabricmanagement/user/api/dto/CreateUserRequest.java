package com.fabricmanagement.user.api.dto;

import com.fabricmanagement.shared.infrastructure.constants.ValidationConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Create User Request DTO
 * 
 * Follows PRINCIPLES.md - uses constants instead of magic numbers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(max = ValidationConstants.MAX_NAME_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String firstName;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(max = ValidationConstants.MAX_NAME_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String lastName;
    
    @Size(max = ValidationConstants.MAX_NAME_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String displayName;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Email(message = ValidationConstants.MSG_INVALID_EMAIL)
    @Pattern(regexp = ValidationConstants.EMAIL_PATTERN, message = ValidationConstants.MSG_INVALID_EMAIL)
    @Size(max = ValidationConstants.MAX_EMAIL_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String email;
    
    @Pattern(regexp = ValidationConstants.PHONE_PATTERN, message = ValidationConstants.MSG_INVALID_PHONE)
    @Size(max = ValidationConstants.MAX_PHONE_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String phone;
    
    private String role;
    
    // Policy-based authorization fields
    private String companyId; // UUID as String (boundary layer)
    
    private String departmentId; // UUID as String (boundary layer)
    
    private String stationId; // UUID as String (boundary layer)
    
    private String jobTitle;
    
    private String userContext; // SUPER_ADMIN, COMPANY_USER, SUPPLIER_USER, etc.
    
    private Map<String, Object> preferences;
    
    private Map<String, Object> settings;
}

