package com.fabricmanagement.company.application.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Query for getting a single company by ID
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetCompanyQuery {
    private UUID companyId;
    private UUID tenantId;
}

