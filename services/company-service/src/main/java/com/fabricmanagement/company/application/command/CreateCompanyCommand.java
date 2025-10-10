package com.fabricmanagement.company.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Command for creating a new company
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCompanyCommand {
    private UUID tenantId;
    private String name;
    private String legalName;
    private String taxId;
    private String registrationNumber;
    private String type;
    private String industry;
    private String description;
    private String website;
    private String logoUrl;
    private String businessType;
    private UUID parentCompanyId;
    private String relationshipType;
    private String createdBy;
}

