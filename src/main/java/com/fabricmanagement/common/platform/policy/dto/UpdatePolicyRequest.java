package com.fabricmanagement.common.platform.policy.dto;

import com.fabricmanagement.common.platform.policy.domain.PolicyEffect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for updating a policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePolicyRequest {

    private String resource;

    private String action;

    private Integer priority;

    private PolicyEffect effect;

    private Boolean enabled;

    private Map<String, Object> conditions;

    private String description;
}

