package com.fabricmanagement.human.compliance.localization.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidateHrPolicyPackRequest(@NotBlank String payload) {}
