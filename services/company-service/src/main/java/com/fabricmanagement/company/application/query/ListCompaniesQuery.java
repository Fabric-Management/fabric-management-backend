package com.fabricmanagement.company.application.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Query for listing all companies for a tenant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListCompaniesQuery {
    private UUID tenantId;
}

