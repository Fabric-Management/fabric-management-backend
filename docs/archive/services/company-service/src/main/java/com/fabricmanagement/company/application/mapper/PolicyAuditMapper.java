package com.fabricmanagement.company.application.mapper;

import com.fabricmanagement.company.api.dto.response.PolicyAuditResponse;
import com.fabricmanagement.shared.domain.policy.PolicyDecisionAudit;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Policy Audit Mapper
 * 
 * Maps PolicyDecisionAudit entity to DTOs.
 */
@Component
public class PolicyAuditMapper {
    
    public PolicyAuditResponse toResponse(PolicyDecisionAudit entity) {
        return PolicyAuditResponse.builder()
            .id(entity.getId())
            .userId(entity.getUserId())
            .companyId(entity.getCompanyId())
            .endpoint(entity.getEndpoint())
            .httpMethod(entity.getHttpMethod())
            .operation(entity.getOperation())
            .decision(entity.getDecision())
            .reason(entity.getReason())
            .policyVersion(entity.getPolicyVersion())
            .latencyMs(entity.getLatencyMs() != null ? entity.getLatencyMs().longValue() : null)
            .correlationId(entity.getCorrelationId())
            .decidedAt(entity.getCreatedAt())
            .metadata(null)
            .build();
    }
    
    public List<PolicyAuditResponse> toResponseList(List<PolicyDecisionAudit> entities) {
        return entities.stream()
            .map(this::toResponse)
            .toList();
    }
}

