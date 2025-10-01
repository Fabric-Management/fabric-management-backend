package com.fabricmanagement.company.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Command for updating company information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanyCommand {
    private UUID companyId;
    private UUID tenantId;
    private String legalName;
    private String description;
    private String website;
    private String logoUrl;
    private String taxId;
    private String registrationNumber;
    private String updatedBy;
}

