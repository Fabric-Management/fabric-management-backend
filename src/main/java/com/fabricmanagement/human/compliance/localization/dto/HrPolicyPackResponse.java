package com.fabricmanagement.human.compliance.localization.dto;

import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPackStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HrPolicyPackResponse(
    UUID id,
    String packCode,
    String countryCode,
    String name,
    String description,
    Integer packVersion,
    HrPolicyPackStatus status,
    Instant effectiveFrom,
    Instant effectiveTo,
    String checksum,
    String parentPackCode,
    String regionCode,
    HrInheritanceModeDto inheritanceMode,
    Instant updatedAt,
    List<PolicyBindingDto> bindings,
    List<RuleVersionDto> ruleVersions
) {
}

