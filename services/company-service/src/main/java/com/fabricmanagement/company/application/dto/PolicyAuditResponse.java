package com.fabricmanagement.company.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Policy Audit Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAuditResponse {
    
    private UUID id;
    private UUID userId;
    private UUID companyId;
    private String endpoint;
    private String httpMethod;
    private String operation;
    private String decision;
    private String reason;
    private String policyVersion;
    private Long latencyMs;
    private String correlationId;
    private LocalDateTime decidedAt;
    private String metadata;
}

