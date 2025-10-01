package com.fabricmanagement.contact.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Verify Contact Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyContactRequest {
    
    @NotBlank(message = "Verification code is required")
    private String code;
}
