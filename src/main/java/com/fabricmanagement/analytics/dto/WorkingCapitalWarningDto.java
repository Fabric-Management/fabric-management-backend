package com.fabricmanagement.analytics.dto;

import lombok.Builder;

/** Warning surfaced by the working-capital view (e.g. insufficient DSO/DPO window). */
@Builder
public record WorkingCapitalWarningDto(String code, String referenceId, String message) {}
