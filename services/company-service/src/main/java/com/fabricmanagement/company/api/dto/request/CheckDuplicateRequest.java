package com.fabricmanagement.company.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for checking duplicate companies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckDuplicateRequest {
    
    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 100, message = "Company name must be between 2 and 100 characters")
    private String name;
    
    @Size(max = 200, message = "Legal name must not exceed 200 characters")
    private String legalName;
    
    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;
    
    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    private String taxId;
    
    @Size(max = 100, message = "Registration number must not exceed 100 characters")
    private String registrationNumber;
}

