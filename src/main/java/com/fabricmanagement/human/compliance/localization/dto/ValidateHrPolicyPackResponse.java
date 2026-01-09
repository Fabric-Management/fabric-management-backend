package com.fabricmanagement.human.compliance.localization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidateHrPolicyPackResponse(boolean valid, String checksum, String message) {}
