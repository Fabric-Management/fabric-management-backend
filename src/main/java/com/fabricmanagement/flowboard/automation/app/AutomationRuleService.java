package com.fabricmanagement.flowboard.automation.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.flowboard.automation.domain.AutomationRule;
import com.fabricmanagement.flowboard.automation.dto.AutomationRuleRequest;
import com.fabricmanagement.flowboard.automation.dto.AutomationRuleResponse;
import com.fabricmanagement.flowboard.automation.infra.repository.AutomationRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationRuleService {

  private final AutomationRuleRepository ruleRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public List<AutomationRuleResponse> getAutomations(UUID boardId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    List<AutomationRule> rules;
    if (boardId != null) {
      rules = ruleRepository.findAllByTenantIdAndBoardId(tenantId, boardId);
    } else {
      rules = ruleRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }
    return rules.stream().map(r -> AutomationRuleResponse.from(r, objectMapper)).toList();
  }

  @Transactional
  public AutomationRuleResponse createRule(AutomationRuleRequest request) {
    var userCtx = currentUser();
    AutomationRule rule =
        AutomationRule.create(
            request.name(),
            request.triggerType(),
            toJson(request.triggerConfig()),
            toJson(request.conditionConfig()),
            request.actionType(),
            toJson(request.actionConfig()),
            request.boardId(),
            userCtx.userId());

    AutomationRule saved = ruleRepository.save(rule);
    return AutomationRuleResponse.from(saved, objectMapper);
  }

  @Transactional
  public AutomationRuleResponse updateRule(UUID id, AutomationRuleRequest request) {
    AutomationRule rule =
        ruleRepository
            .findByIdAndTenantId(id, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new NotFoundException("Automation rule not found"));

    rule.update(
        request.name(),
        request.description(),
        request.triggerType(),
        toJson(request.triggerConfig()),
        toJson(request.conditionConfig()),
        request.actionType(),
        toJson(request.actionConfig()),
        request.boardId());

    AutomationRule saved = ruleRepository.save(rule);
    return AutomationRuleResponse.from(saved, objectMapper);
  }

  @Transactional
  public AutomationRuleResponse toggleActive(UUID id, boolean active) {
    AutomationRule rule =
        ruleRepository
            .findByIdAndTenantId(id, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new NotFoundException("Automation rule not found"));

    rule.toggleActive(active);
    AutomationRule saved = ruleRepository.save(rule);
    return AutomationRuleResponse.from(saved, objectMapper);
  }

  @Transactional
  public void deleteRule(UUID id) {
    AutomationRule rule =
        ruleRepository
            .findByIdAndTenantId(id, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new NotFoundException("Automation rule not found"));

    rule.delete();
    ruleRepository.save(rule);
  }

  private String toJson(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return "{}";
    }
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize automation config to JSON", e);
      return "{}";
    }
  }

  private AuthenticatedUserContext currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof AuthenticatedUserContext ctx) {
      return ctx;
    }
    throw new NotFoundException("AuthenticatedUserContext not found in SecurityContext");
  }
}
