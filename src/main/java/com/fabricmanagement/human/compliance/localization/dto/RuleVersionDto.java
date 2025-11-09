package com.fabricmanagement.human.compliance.localization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleVersionDto(
    String ruleType,
    String payloadHash,
    String payload
) {
}

