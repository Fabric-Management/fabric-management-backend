package com.fabricmanagement.company.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Command for deleting a company (soft delete)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteCompanyCommand {
    private UUID companyId;
    private UUID tenantId;
    private String deletedBy;
}

