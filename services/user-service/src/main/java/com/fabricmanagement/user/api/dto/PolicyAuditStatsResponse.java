package com.fabricmanagement.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Policy Audit Statistics Response DTO
 * 
 * Aggregated statistics about policy decisions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAuditStatsResponse {
    
    private Long totalDecisions;
    private Long allowDecisions;
    private Long denyDecisions;
    private Double denyRate; // Percentage
    private Double averageLatencyMs;
}

