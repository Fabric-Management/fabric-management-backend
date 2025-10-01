package com.fabricmanagement.company.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Command for updating company subscription
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubscriptionCommand {
    private UUID companyId;
    private UUID tenantId;
    private String plan;
    private Integer maxUsers;
    private LocalDateTime endDate;
    private String updatedBy;
}

