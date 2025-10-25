package com.fabricmanagement.common.platform.policy.dto;

import com.fabricmanagement.common.platform.policy.domain.PolicyEffect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePolicyRequest {

    @NotBlank(message = "Policy ID is required")
    private String policyId;

    @NotBlank(message = "Resource is required")
    private String resource;

    @NotBlank(message = "Action is required")
    private String action;

    @NotNull(message = "Priority is required")
    private Integer priority;

    @NotNull(message = "Effect is required")
    private PolicyEffect effect;

    private Map<String, Object> conditions;

    private String description;
}

