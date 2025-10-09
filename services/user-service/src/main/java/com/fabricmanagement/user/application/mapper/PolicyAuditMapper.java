package com.fabricmanagement.user.application.mapper;

import com.fabricmanagement.shared.domain.policy.PolicyDecisionAudit;
import com.fabricmanagement.user.api.dto.PolicyAuditResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Policy Audit Mapper
 * 
 * Maps PolicyDecisionAudit entity to DTOs.
 */
@Component
public class PolicyAuditMapper {
    
    /**
     * Map PolicyDecisionAudit to PolicyAuditResponse
     */
    public PolicyAuditResponse toResponse(PolicyDecisionAudit entity) {
        return PolicyAuditResponse.builder()
            .id(entity.getId())
            .userId(entity.getUserId())
            .companyId(entity.getCompanyId())
            .endpoint(entity.getEndpoint())
            .httpMethod(entity.getHttpMethod())
            .operation(entity.getOperation()) // Already String
            .decision(entity.getDecision())
            .reason(entity.getReason())
            .policyVersion(entity.getPolicyVersion())
            .latencyMs(entity.getLatencyMs() != null ? entity.getLatencyMs().longValue() : null)
            .correlationId(entity.getCorrelationId())
            .decidedAt(entity.getCreatedAt())
            .metadata(null) // Not stored in current entity version
            .build();
    }
    
    /**
     * Map list of PolicyDecisionAudit to list of responses
     */
    public List<PolicyAuditResponse> toResponseList(List<PolicyDecisionAudit> entities) {
        return entities.stream()
            .map(this::toResponse)
            .toList();
    }
}

