package com.fabricmanagement.platform.user.security;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContextResolver;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserType;
import com.fabricmanagement.platform.user.dto.OnboardingCompanyCapability;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Own-profile identity and fresh, organisation-wide company setup authority. */
@Component
@RequiredArgsConstructor
public class OnboardingAccessService {

  private final UserRepository userRepository;
  private final PermissionEvaluator permissions;
  private final AuthenticatedUserContextResolver contextResolver;

  public User requireCurrentUser(UUID userId) {
    var context =
        contextResolver
            .resolve(SecurityContextHolder.getContext().getAuthentication())
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "User not authenticated", "USER_NOT_AUTHENTICATED", 401));
    UUID tenantId = TenantContext.requireTenantId();
    if (userId == null
        || !userId.equals(context.userId())
        || !tenantId.equals(context.tenantId())) {
      throw new PlatformDomainException(
          "Onboarding belongs to the authenticated user", "ONBOARDING_IDENTITY_MISMATCH", 403);
    }
    User user =
        userRepository
            .findByIdWithPermissionData(tenantId, userId)
            .filter(u -> Boolean.TRUE.equals(u.getIsActive()) && u.getDeletedAt() == null)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Active tenant membership is required",
                        "ONBOARDING_MEMBERSHIP_REQUIRED",
                        403));
    return user;
  }

  public void requireCompanySetup(User user) {
    OnboardingCompanyCapability capability = companySetupCapability(user);
    if (!capability.allowed()) {
      throw new PlatformDomainException(
          "Organisation-wide company setup authority is required", capability.reason(), 403);
    }
  }

  public OnboardingCompanyCapability companySetupCapability(User user) {
    String reason = null;
    UUID tenantId = TenantContext.requireTenantId();
    if (TenantContext.SYSTEM_TENANT_ID.equals(tenantId)
        || TenantContext.TEMPLATE_TENANT_ID.equals(tenantId)
        || !Objects.equals(tenantId, user.getTenantId())
        || !Boolean.TRUE.equals(user.getIsActive())
        || user.getDeletedAt() != null) {
      reason = "ONBOARDING_MEMBERSHIP_REQUIRED";
    } else if (user.isDemoSeed() || user.getUserType() != UserType.INTERNAL) {
      reason = "ONBOARDING_COMPANY_REQUIRES_REAL_MEMBER";
    } else {
      var role = user.getRole();
      String roleCode =
          role != null && Boolean.TRUE.equals(role.getIsActive()) && role.getDeletedAt() == null
              ? role.getRoleCode()
              : null;
      var departments =
          user.getUserDepartments().stream()
              .filter(assignment -> Boolean.TRUE.equals(assignment.getIsActive()))
              .map(com.fabricmanagement.platform.user.domain.UserDepartment::getDepartment)
              .filter(Objects::nonNull)
              .filter(d -> Boolean.TRUE.equals(d.getIsActive()) && d.getDeletedAt() == null)
              .map(com.fabricmanagement.platform.organization.domain.Department::getDepartmentCode)
              .filter(Objects::nonNull)
              .distinct()
              .toList();
      DataScope scope =
          permissions
              .evaluateFresh(user.getTenantId(), roleCode, departments, user.getId())
              .scopeOf("organization", "configure");
      if (scope == null || scope.compareTo(DataScope.ORGANIZATION) < 0) {
        reason = "ONBOARDING_COMPANY_PERMISSION_REQUIRED";
      }
    }
    return new OnboardingCompanyCapability(
        PermissionKey.ORGANIZATION_CONFIGURE, reason == null, reason, null, false);
  }
}
