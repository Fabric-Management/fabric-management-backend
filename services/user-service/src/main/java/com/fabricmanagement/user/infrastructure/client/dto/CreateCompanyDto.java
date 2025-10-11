package com.fabricmanagement.user.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCompanyDto {
    
    private String name;
    private String legalName;
    private String taxId;
    private String registrationNumber;
    private String type;
    private String industry;
    private String description;
    private String website;
    private String businessType;
    private UUID parentCompanyId;
    private String relationshipType;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String district;
    private String postalCode;
    private String country;
}

