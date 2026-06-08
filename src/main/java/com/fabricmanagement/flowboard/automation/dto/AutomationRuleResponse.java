package com.fabricmanagement.flowboard.automation.dto;

import com.fabricmanagement.flowboard.automation.domain.AutomationActionType;
import com.fabricmanagement.flowboard.automation.domain.AutomationRule;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AutomationRuleResponse(
    UUID id,
    String name,
    String description,
    AutomationTriggerType triggerType,
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
        Map<String, Object> triggerConfig,
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
        Map<String, Object> conditionConfig,
    AutomationActionType actionType,
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
        Map<String, Object> actionConfig,
    UUID boardId,
    boolean isActive,
    long executionCount,
    Instant lastExecutedAt,
    Instant createdAt) {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  public static AutomationRuleResponse from(AutomationRule rule, ObjectMapper mapper) {
    return new AutomationRuleResponse(
        rule.getId(),
        rule.getName(),
        rule.getDescription(),
        rule.getTriggerType(),
        parseToMap(rule.getTriggerConfig(), mapper),
        parseToMap(rule.getConditionConfig(), mapper),
        rule.getActionType(),
        parseToMap(rule.getActionConfig(), mapper),
        rule.getBoardId(),
        rule.isActive(),
        rule.getExecutionCount(),
        rule.getLastExecutedAt(),
        rule.getCreatedAt());
  }

  private static Map<String, Object> parseToMap(String json, ObjectMapper mapper) {
    if (json == null || json.isBlank() || json.equals("{}")) {
      return Map.of();
    }
    try {
      return mapper.readValue(json, MAP_TYPE);
    } catch (JsonProcessingException e) {
      return Map.of("error", "could_not_parse_config");
    }
  }
}
