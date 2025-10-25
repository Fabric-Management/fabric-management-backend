package com.fabricmanagement.common.platform.policy.dto;

import com.fabricmanagement.common.platform.policy.domain.Policy;
import com.fabricmanagement.common.platform.policy.domain.PolicyEffect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDto {

    private UUID id;
    private String policyId;
    private String resource;
    private String action;
    private Integer priority;
    private PolicyEffect effect;
    private Boolean enabled;
    private Map<String, Object> conditions;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public static PolicyDto from(Policy policy) {
        return PolicyDto.builder()
            .id(policy.getId())
            .policyId(policy.getPolicyId())
            .resource(policy.getResource())
            .action(policy.getAction())
            .priority(policy.getPriority())
            .effect(policy.getEffect())
            .enabled(policy.getEnabled())
            .conditions(policy.getConditions())
            .description(policy.getDescription())
            .createdAt(policy.getCreatedAt())
            .updatedAt(policy.getUpdatedAt())
            .build();
    }
}

