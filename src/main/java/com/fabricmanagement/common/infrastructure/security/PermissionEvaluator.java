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
  private final com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository
      departmentRepository;

  /**
   * Calculates the effective permissions for a user via logic precedence. 1. ADMIN/PLATFORM_ADMIN
   * bypass 2. Per-Dept hierarchical retrieval with tenant overriding system default 3. Cross-Dept
   * merging (escalating scope) 4. User-Specific overrides applied last
   */
  @org.springframework.cache.annotation.Cacheable(
      value = "permissions",
      key = "#tenantId + '_' + #userId")
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

    List<String> userDepts =
        (departmentCodes == null || departmentCodes.isEmpty())
            ? List.of((String) null)
            : departmentCodes;

    // Expand search width by gathering all ancestors for each assigned department
    // Depth limited to 5 implicitly in practice, but usually tree isn't too deep
    java.util.Set<String> allAncestorCodes = new java.util.HashSet<>();
    Map<String, List<String>> deptLineages = new HashMap<>();

    for (String deptCode : userDepts) {
      if (deptCode == null) continue;
      List<String> ancestors = departmentRepository.findAncestorCodes(tenantId, deptCode);
      if (ancestors.size() > 5) {
        ancestors = ancestors.subList(0, 5); // 5 levels depth limit
      }
      allAncestorCodes.addAll(ancestors);
      deptLineages.put(deptCode, ancestors); // Child -> Parent order
    }

    List<String> evalDeptsBatch = new java.util.ArrayList<>(allAncestorCodes);
    if (evalDeptsBatch.isEmpty()) {
      evalDeptsBatch.add(null);
    }

    // Single query to fetch all templates for user's extended dependencies
    List<PermissionTemplate> templates =
        templateRepo.findEffectiveTemplatesForDepartments(tenantId, roleCode, evalDeptsBatch);
    log.debug(
        "Found {} templates for role={}, depts={}", templates.size(), roleCode, evalDeptsBatch);

    // Group global templates (System fallback ones without explicit department)
    List<PermissionTemplate> globalTemplates = new java.util.ArrayList<>();
    Map<String, List<PermissionTemplate>> deptTemplatesMap = new HashMap<>();

    for (PermissionTemplate tpl : templates) {
      if (tpl.getDepartmentCode() == null) {
        globalTemplates.add(tpl);
      } else {
        deptTemplatesMap
            .computeIfAbsent(tpl.getDepartmentCode(), k -> new java.util.ArrayList<>())
            .add(tpl);
      }
    }

    // Process global templates map once to avoid re-evaluating tenant priorities
    Map<String, Map<String, DataScope>> globalLocalMap = new HashMap<>();
    for (PermissionTemplate tpl : globalTemplates) {
      globalLocalMap.putIfAbsent(tpl.getResource(), new HashMap<>());
      globalLocalMap.get(tpl.getResource()).putIfAbsent(tpl.getAction(), tpl.getDataScope());
    }

    // Process Templates dynamically per mapped department to maintain escalation precedence
    for (String userDeptCode : userDepts) {
      List<String> lineage =
          userDeptCode == null
              ? List.of()
              : deptLineages.getOrDefault(userDeptCode, List.of(userDeptCode));
      List<String> reversedLineage = new java.util.ArrayList<>(lineage);
      java.util.Collections.reverse(reversedLineage); // Process ROOT to LEAF

      Map<String, Map<String, DataScope>> lineageMap = new HashMap<>();

      // 1. Apply Globals
      for (Map.Entry<String, Map<String, DataScope>> resEntry : globalLocalMap.entrySet()) {
        lineageMap.putIfAbsent(resEntry.getKey(), new HashMap<>());
        lineageMap.get(resEntry.getKey()).putAll(resEntry.getValue());
      }

      // 2. Apply Hierarchical Levels (Parent then Child)
      for (String currentDept : reversedLineage) {
        List<PermissionTemplate> deptTpls = deptTemplatesMap.getOrDefault(currentDept, List.of());
        Map<String, Map<String, DataScope>> currentMap = new HashMap<>();
        for (PermissionTemplate tpl : deptTpls) {
          currentMap.putIfAbsent(tpl.getResource(), new HashMap<>());
          currentMap
              .get(tpl.getResource())
              .putIfAbsent(tpl.getAction(), tpl.getDataScope()); // Tenant wins over system
        }

        // Overwrite lineage (Child wins)
        for (Map.Entry<String, Map<String, DataScope>> resEntry : currentMap.entrySet()) {
          lineageMap.putIfAbsent(resEntry.getKey(), new HashMap<>());
          lineageMap.get(resEntry.getKey()).putAll(resEntry.getValue());
        }
      }

      // 3. Cross-Department Sibling Escalation (Merge to evaluatedPermissions)
      for (Map.Entry<String, Map<String, DataScope>> resEntry : lineageMap.entrySet()) {
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
