package com.fabricmanagement.human.compliance.localization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HrCountryPackMappingResponse(
    UUID id,
    String countryCode,
    String packCode
) {
}

