package com.fabricmanagement.company.application.dto.company.request;

import com.fabricmanagement.company.domain.valueobject.*;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Request DTO for updating a company.
 */
@Builder
public record UpdateCompanyRequest(
    @Size(max = 255, message = "Company name must not exceed 255 characters")
    String companyName,

    @Size(max = 255, message = "Display name must not exceed 255 characters")
    String displayName,

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    String description,

    @Size(max = 100, message = "Registration number must not exceed 100 characters")
    String registrationNumber,

    @Size(max = 100, message = "Tax number must not exceed 100 characters")
    String taxNumber,

    LocalDate foundedDate,

    Industry industry,

    CompanyType companyType,

    CompanySize companySize,

    BigDecimal annualRevenue,

    Currency currency,

    @Size(max = 10, message = "Fiscal year end must not exceed 10 characters")
    String fiscalYearEnd,

    @Size(max = 500, message = "Website URL must not exceed 500 characters")
    String website,

    Set<String> certifications,

    Set<String> businessLicenses
) {
}