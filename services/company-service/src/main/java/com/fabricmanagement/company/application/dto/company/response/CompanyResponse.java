package com.fabricmanagement.company.application.dto.company.response;

import com.fabricmanagement.company.domain.valueobject.*;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for company information (basic view).
 */
@Builder
public record CompanyResponse(
    UUID id,
    String companyName,
    String displayName,
    String description,
    Industry industry,
    CompanyType companyType,
    CompanySize companySize,
    CompanyStatus status,
    String website,
    LocalDate foundedDate,
    BigDecimal annualRevenue,
    Currency currency,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public String getFullCompanyName() {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return companyName != null ? companyName : "";
    }
}