package com.fabricmanagement.flowboard.automation.dto;

import com.fabricmanagement.flowboard.automation.domain.AutomationActionType;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record AutomationRuleRequest(
    @NotBlank String name,
    String description,
    @NotNull AutomationTriggerType triggerType,
    Map<String, Object> triggerConfig,
    Map<String, Object> conditionConfig,
    @NotNull AutomationActionType actionType,
    Map<String, Object> actionConfig,
    UUID boardId) {}
