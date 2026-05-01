package com.fabricmanagement.common.infrastructure.security.dto;

import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.List;
import java.util.Map;

/**
 * Note: When isSuperAdmin is true, the permissions map may be empty. Always use can() and scopeOf()
 * methods instead of directly accessing the map. can() and scopeOf() handle the superAdmin case
 * automatically.
 */
public record PermissionResult(
    Map<String, Map<String, DataScope>> permissions, boolean isSuperAdmin) {

  public boolean can(String resource, String action) {
    if (isSuperAdmin) {
      return true;
    }
    return permissions.containsKey(resource) && permissions.get(resource).containsKey(action);
  }

  public DataScope scopeOf(String resource, String action) {
    if (isSuperAdmin) {
      return DataScope.GLOBAL;
    }
    if (!permissions.containsKey(resource)) {
      return null;
    }
    return permissions.get(resource).get(action);
  }

  public List<EffectivePermission> toList() {
    if (permissions == null) {
      return List.of();
    }
    return permissions.entrySet().stream()
        .flatMap(
            r ->
                r.getValue().entrySet().stream()
                    .map(a -> new EffectivePermission(r.getKey(), a.getKey(), a.getValue())))
        .toList();
  }
}
