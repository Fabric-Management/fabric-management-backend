package com.fabricmanagement.company.application.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Query for searching companies by name
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchCompaniesQuery {
    private UUID tenantId;
    private String searchTerm;
}

