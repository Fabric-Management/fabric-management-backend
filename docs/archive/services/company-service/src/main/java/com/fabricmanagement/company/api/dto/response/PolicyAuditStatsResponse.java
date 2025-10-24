package com.fabricmanagement.company.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Policy Audit Statistics Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAuditStatsResponse {
    
    private Long totalDecisions;
    private Long allowDecisions;
    private Long denyDecisions;
    private Double denyRate;
    private Double averageLatencyMs;
}

