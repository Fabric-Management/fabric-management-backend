package com.fabricmanagement.platform.user.security;

import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads the authorisation inputs of an active tenant member. */
@Component
@RequiredArgsConstructor
public class PermissionIdentityReader {

  private final UserRepository users;

  @Transactional(readOnly = true)
  public Optional<PermissionIdentity> find(UUID tenantId, UUID userId) {
    if (tenantId == null || userId == null) {
      return Optional.empty();
    }
    return users
        .findByIdWithPermissionData(tenantId, userId)
        .filter(user -> Boolean.TRUE.equals(user.getIsActive()) && user.getDeletedAt() == null)
        .map(PermissionIdentityReader::fromActiveUser);
  }

  public static PermissionIdentity fromActiveUser(User user) {
    var role = user.getRole();
    String roleCode =
        role != null && Boolean.TRUE.equals(role.getIsActive()) && role.getDeletedAt() == null
            ? role.getRoleCode()
            : null;
    var departments =
        user.getUserDepartments().stream()
            .filter(assignment -> Boolean.TRUE.equals(assignment.getIsActive()))
            .map(UserDepartment::getDepartment)
            .filter(Objects::nonNull)
            .filter(
                department ->
                    Boolean.TRUE.equals(department.getIsActive())
                        && department.getDeletedAt() == null)
            .map(department -> department.getDepartmentCode())
            .filter(code -> code != null && !code.isBlank())
            .distinct()
            .toList();
    return new PermissionIdentity(roleCode, departments);
  }
}
