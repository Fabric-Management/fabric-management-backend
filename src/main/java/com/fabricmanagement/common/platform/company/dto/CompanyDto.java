package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private String companyName;
    private String taxId;
    private CompanyType companyType;
    private UUID parentCompanyId;
    private Boolean isActive;
    private Boolean isTenant;
    private Instant createdAt;
    private Instant updatedAt;

    public static CompanyDto from(Company company) {
        return CompanyDto.builder()
            .id(company.getId())
            .tenantId(company.getTenantId())
            .uid(company.getUid())
            .companyName(company.getCompanyName())
            .taxId(company.getTaxId())
            .companyType(company.getCompanyType())
            .parentCompanyId(company.getParentCompanyId())
            .isActive(company.getIsActive())
            .isTenant(company.isTenant())
            .createdAt(company.getCreatedAt())
            .updatedAt(company.getUpdatedAt())
            .build();
    }
}

