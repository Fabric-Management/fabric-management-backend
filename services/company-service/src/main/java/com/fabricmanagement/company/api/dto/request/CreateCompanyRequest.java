package com.fabricmanagement.company.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a new company
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCompanyRequest {
    
    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 100, message = "Company name must be between 2 and 100 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-&.,()'/]+$", message = "Company name contains invalid characters")
    private String name;
    
    @Size(max = 200, message = "Legal name must not exceed 200 characters")
    private String legalName;
    
    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    private String taxId;
    
    @Size(max = 100, message = "Registration number must not exceed 100 characters")
    private String registrationNumber;
    
    @NotNull(message = "Company type is required")
    private String type; // CORPORATION, LLC, PARTNERSHIP, etc.
    
    @NotNull(message = "Industry is required")
    private String industry; // TECHNOLOGY, MANUFACTURING, etc.
    
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;
    
    private String businessType; // INTERNAL, CUSTOMER, SUPPLIER, SUBCONTRACTOR
    
    private String parentCompanyId; // Parent company UUID (for external companies)
    
    private String relationshipType; // Business relationship type
    
    @Pattern(regexp = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})[/\\w .-]*/?$", message = "Invalid website URL")
    private String website;
    
    private String logoUrl;
    
    @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
    private String addressLine1;
    
    @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
    private String addressLine2;
    
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;
    
    @Size(max = 100, message = "District must not exceed 100 characters")
    private String district;
    
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;
    
    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;
    
    // Optional: For internal service-to-service calls (onboarding flow)
    // If provided, overrides SecurityContext (allows calls without JWT)
    private UUID tenantId;      // UUID type (internal call only)
    private String createdBy;   // Audit trail
}

