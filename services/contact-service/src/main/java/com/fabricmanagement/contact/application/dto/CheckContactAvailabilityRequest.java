package com.fabricmanagement.contact.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Check Contact Availability Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckContactAvailabilityRequest {
    
    @NotBlank(message = "Contact value is required")
    private String contactValue;
}
