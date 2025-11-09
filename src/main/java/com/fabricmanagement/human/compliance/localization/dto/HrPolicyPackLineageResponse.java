package com.fabricmanagement.human.compliance.localization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HrPolicyPackLineageResponse(
    String packCode,
    Integer packVersion,
    String countryCode,
    String regionCode,
    String parentPackCode,
    HrInheritanceModeDto inheritanceMode,
    List<String> lineageCodes,
    String resolvedPayload
) {
}

