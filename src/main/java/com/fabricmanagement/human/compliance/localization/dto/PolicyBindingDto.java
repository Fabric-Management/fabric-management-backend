package com.fabricmanagement.human.compliance.localization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PolicyBindingDto(
    String policyInterface, String strategyBean, String configReference) {}
