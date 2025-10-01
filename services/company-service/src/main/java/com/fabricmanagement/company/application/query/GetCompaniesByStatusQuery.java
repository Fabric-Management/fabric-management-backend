package com.fabricmanagement.company.application.query;

import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Query for getting companies by status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetCompaniesByStatusQuery {
    private UUID tenantId;
    private CompanyStatus status;
}

