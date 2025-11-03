package com.fabricmanagement.common.platform.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Tenant statistics for platform admin dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStatistics {
    private UUID tenantId;
    private String tenantUid;
    private String companyName;
    private Long userCount;
    private Long companyCount;
    private Long subscriptionCount;
    private Boolean isActive;
}

