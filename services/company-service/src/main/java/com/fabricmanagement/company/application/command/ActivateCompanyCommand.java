package com.fabricmanagement.company.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Command for activating a company
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivateCompanyCommand {
    private UUID companyId;
    private UUID tenantId;
    private String updatedBy;
}

