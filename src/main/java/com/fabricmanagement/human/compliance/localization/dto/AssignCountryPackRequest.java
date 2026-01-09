package com.fabricmanagement.human.compliance.localization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AssignCountryPackRequest(
    @NotBlank @Pattern(regexp = "^[A-Z]{2,8}$") String countryCode,
    @NotBlank @Pattern(regexp = "^[A-Z0-9_-]{2,100}$") String packCode) {}
