package com.fabricmanagement.company.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Command for updating company settings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanySettingsCommand {
    private UUID companyId;
    private UUID tenantId;
    private Map<String, Object> settings;
    private String updatedBy;
}

