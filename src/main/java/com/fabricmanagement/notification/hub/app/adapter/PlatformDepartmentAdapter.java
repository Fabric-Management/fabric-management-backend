package com.fabricmanagement.notification.hub.app.adapter;

import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import com.fabricmanagement.platform.organization.app.DepartmentService;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.SystemDepartment;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Platform modülünün DepartmentService'ini kullanarak DepartmentRecipientPort'u implemente eden
 * adaptör. ACL Pattern.
 */
@Component
@RequiredArgsConstructor
public class PlatformDepartmentAdapter implements DepartmentRecipientPort {

  private final DepartmentService departmentService;
  private final UserQueryService userQueryService;

  @Override
  public List<UUID> findUsersByDepartmentKeyword(UUID tenantId, String... keywords) {
    List<Department> deps =
        departmentService.getActiveDepartmentsByTenant(tenantId).stream()
            .filter(
                d ->
                    matchesAny(d.getDepartmentCode(), keywords)
                        || matchesAny(d.getDepartmentName(), keywords))
            .toList();

    return deps.stream()
        .flatMap(d -> userQueryService.findByDepartments(tenantId, Set.of(d.getId())).stream())
        .map(UserDto::getId)
        .distinct()
        .toList();
  }

  @Override
  public List<UUID> findManagersByDepartmentKeyword(UUID tenantId, String... keywords) {
    List<Department> deps =
        departmentService.getActiveDepartmentsByTenant(tenantId).stream()
            .filter(
                d ->
                    matchesAny(d.getDepartmentCode(), keywords)
                        || matchesAny(d.getDepartmentName(), keywords))
            .toList();

    List<UUID> managers =
        deps.stream().map(Department::getManagerId).filter(Objects::nonNull).distinct().toList();

    if (managers.isEmpty()) {
      return deps.stream()
          .flatMap(d -> userQueryService.findByDepartments(tenantId, Set.of(d.getId())).stream())
          .map(UserDto::getId)
          .distinct()
          .toList();
    }
    return managers;
  }

  private boolean matchesAny(String value, String[] targets) {
    if (value == null) {
      return false;
    }
    String upper = value.toUpperCase(Locale.ENGLISH);
    for (String target : targets) {
      String canonicalCode =
          SystemDepartment.fromCode(target).map(SystemDepartment::code).orElse(null);
      if (canonicalCode != null && upper.equals(canonicalCode)) {
        return true;
      }
      if (upper.contains(target.toUpperCase(Locale.ENGLISH))) {
        return true;
      }
    }
    return false;
  }
}
