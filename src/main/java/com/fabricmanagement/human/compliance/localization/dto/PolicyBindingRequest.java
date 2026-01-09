package com.fabricmanagement.human.compliance.localization.dto;

import jakarta.validation.constraints.NotBlank;

public record PolicyBindingRequest(
    @NotBlank String policyInterface, @NotBlank String strategyBean, String configReference) {}
