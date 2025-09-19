package com.fabricmanagement.contact.infrastructure.integration.company;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for company information from company-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDto {
    
    private UUID id;
    private String name;
    private String industry;
    private String website;
    private String taxId;
    private String registrationNumber;
    private Integer foundedYear;
    private String companySize;
    private String status;
    private UUID tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
