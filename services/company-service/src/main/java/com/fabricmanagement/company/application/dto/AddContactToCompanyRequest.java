package com.fabricmanagement.company.application.dto;

import com.fabricmanagement.shared.infrastructure.constants.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Add Contact to Company Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddContactToCompanyRequest {
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(max = ValidationConstants.MAX_EMAIL_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    @Pattern(regexp = ValidationConstants.EMAIL_PATTERN, message = ValidationConstants.MSG_INVALID_EMAIL)
    private String contactValue;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    private String contactType;
    
    @NotNull(message = ValidationConstants.MSG_REQUIRED)
    private Boolean isPrimary;
}

