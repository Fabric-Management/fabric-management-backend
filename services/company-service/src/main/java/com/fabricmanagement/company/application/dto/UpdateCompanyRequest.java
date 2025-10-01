package com.fabricmanagement.company.application.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating company information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanyRequest {
    
    @Size(max = 200, message = "Legal name must not exceed 200 characters")
    private String legalName;
    
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;
    
    @Pattern(regexp = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})[/\\w .-]*/?$", message = "Invalid website URL")
    private String website;
    
    private String logoUrl;
    
    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    private String taxId;
    
    @Size(max = 100, message = "Registration number must not exceed 100 characters")
    private String registrationNumber;
}

