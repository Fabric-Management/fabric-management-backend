package com.fabricmanagement.human.compliance.localization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateHrPolicyPackRequest(
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9_-]{2,100}$")
    String packCode,

    @NotBlank
    @Pattern(regexp = "^[A-Z]{2,8}$")
    String countryCode,

    @NotBlank
    String name,

    String description,

    @NotBlank
    String payload,

    String parentPackCode,

    String regionCode,

    HrInheritanceModeDto inheritanceMode,

    @Valid
    List<PolicyBindingRequest> bindings,

    @Valid
    List<RuleVersionRequest> ruleVersions
) {
}

