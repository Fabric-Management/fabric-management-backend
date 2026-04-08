package com.fabricmanagement.common.infrastructure.security;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionOverride;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.infra.repository.PermissionOverrideRepository;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionEvaluator {

  private final PermissionTemplateRepository templateRepo;
  private final PermissionOverrideRepository overrideRepo;

  /**
   * Calculates the effective permissions for a user via logic precedence. 1. ADMIN/PLATFORM_ADMIN
   * bypass 2. Per-Dept retrieval with tenant overriding system default 3. Cross-Dept merging
   * (escalating scope) 4. User-Specific overrides applied last
   */
  public PermissionResult evaluate(
      UUID tenantId, String roleCode, List<String> departmentCodes, UUID userId) {
    log.debug(
        "Evaluating permissions for user={}, role={}, departments={}",
        userId,
        roleCode,
        departmentCodes);
    if (roleCode == null) {
      return new PermissionResult(Map.of(), false);
    }

    // Fast-path: Super user bypasses all complex calculations
    if ("ADMIN".equals(roleCode) || "PLATFORM_ADMIN".equals(roleCode)) {
      return new PermissionResult(Map.of(), true);
    }

    Map<String, Map<String, DataScope>> evaluatedPermissions = new HashMap<>();

    List<String> evalDepts =
        (departmentCodes == null || departmentCodes.isEmpty())
            ? List.of((String) null)
            : departmentCodes;

    // TODO: Optimize to single query when department count > 1.
    // Process Templates
    for (String deptCode : evalDepts) {
      List<PermissionTemplate> templates =
          templateRepo.findEffectiveTemplates(tenantId, roleCode, deptCode);
      log.debug("Found {} templates for role={}, dept={}", templates.size(), roleCode, deptCode);

      // Dept-Level cache to enforce Rule 2: Tenant wins over system default (due to NULLS LAST
      // ORDER)
      Map<String, Map<String, DataScope>> deptLocalMap = new HashMap<>();

      for (PermissionTemplate tpl : templates) {
        deptLocalMap.putIfAbsent(tpl.getResource(), new HashMap<>());
        // putIfAbsent prevents system default overriding previously stored tenant-specific
        deptLocalMap.get(tpl.getResource()).putIfAbsent(tpl.getAction(), tpl.getDataScope());
      }

      // Cross-Department Escalation
      for (Map.Entry<String, Map<String, DataScope>> resEntry : deptLocalMap.entrySet()) {
        for (Map.Entry<String, DataScope> actionEntry : resEntry.getValue().entrySet()) {
          mergeHigherScope(
              evaluatedPermissions,
              resEntry.getKey(),
              actionEntry.getKey(),
              actionEntry.getValue());
        }
      }
    }

    // Apply User Overrides
    if (userId != null && tenantId != null) {
      List<PermissionOverride> overrides = overrideRepo.findActiveOverrides(tenantId, userId);
      log.debug("Applied {} overrides for user={}", overrides.size(), userId);
      for (PermissionOverride override : overrides) {
        if (override.getDataScope() == null) {
          revoke(evaluatedPermissions, override.getResource(), override.getAction());
        } else {
          forceSet(
              evaluatedPermissions,
              override.getResource(),
              override.getAction(),
              override.getDataScope());
        }
      }
    }

    PermissionResult result = new PermissionResult(evaluatedPermissions, false);
    log.debug(
        "Final permission result: {} entries, superAdmin={}",
        evaluatedPermissions.size(),
        result.isSuperAdmin());
    return result;
  }

  private void mergeHigherScope(
      Map<String, Map<String, DataScope>> perms, String resource, String action, DataScope scope) {
    perms.putIfAbsent(resource, new HashMap<>());
    Map<String, DataScope> actionMap = perms.get(resource);

    DataScope existing = actionMap.get(action);
    if (existing == null || scope.compareTo(existing) > 0) {
      actionMap.put(action, scope);
    }
  }

  private void forceSet(
      Map<String, Map<String, DataScope>> perms, String resource, String action, DataScope scope) {
    perms.putIfAbsent(resource, new HashMap<>());
    perms.get(resource).put(action, scope);
  }

  private void revoke(Map<String, Map<String, DataScope>> perms, String resource, String action) {
    if (perms.containsKey(resource)) {
      perms.get(resource).remove(action);
      if (perms.get(resource).isEmpty()) {
        perms.remove(resource);
      }
    }
  }
}
