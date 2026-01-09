package com.fabricmanagement.human.compliance.localization.dto;

import jakarta.validation.constraints.NotBlank;

public record RuleVersionRequest(@NotBlank String ruleType, @NotBlank String payload) {}
