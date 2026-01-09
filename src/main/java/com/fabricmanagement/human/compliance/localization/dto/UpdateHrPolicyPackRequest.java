package com.fabricmanagement.human.compliance.localization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateHrPolicyPackRequest(
    @NotBlank String name,
    String description,
    @NotBlank String payload,
    String parentPackCode,
    String regionCode,
    HrInheritanceModeDto inheritanceMode,
    @Valid List<PolicyBindingRequest> bindings,
    @Valid List<RuleVersionRequest> ruleVersions) {}
