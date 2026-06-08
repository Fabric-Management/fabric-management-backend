package com.fabricmanagement.flowboard.automation.dto;

import com.fabricmanagement.flowboard.automation.domain.AutomationActionType;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record AutomationRuleRequest(
    @NotBlank String name,
    String description,
    @NotNull AutomationTriggerType triggerType,
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
        Map<String, Object> triggerConfig,
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
        Map<String, Object> conditionConfig,
    @NotNull AutomationActionType actionType,
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
        Map<String, Object> actionConfig,
    UUID boardId) {}
