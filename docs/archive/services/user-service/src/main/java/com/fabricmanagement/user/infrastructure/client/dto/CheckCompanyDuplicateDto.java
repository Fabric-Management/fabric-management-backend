package com.fabricmanagement.user.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for checking company duplicates during tenant registration
 * Maps to CompanyService's CheckDuplicateRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckCompanyDuplicateDto {
    
    private String name;
    private String legalName;
    private String country;
    private String taxId;
    private String registrationNumber;
}

