package com.fabricmanagement.platform.user.security;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContextResolver;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.PlatformAuthority;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.app.UserProfilePermissionService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.value.ProfileCategory;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserContactDetailsAccess {

  private final UserRepository users;
  private final AuthenticatedUserContextResolver contexts;
  private final PermissionIdentityReader identities;
  private final PermissionEvaluator permissions;
  private final UserProfilePermissionService profiles;

  public void requireRead(UUID userId) {
    requireUser(userId);
    var actor = currentActor();
    identities.find(actor.tenantId(), actor.userId()).orElseThrow(UserContactDetailsAccess::denied);
    if (!profiles.canViewProfile(actor.userId(), userId, ProfileCategory.PERSONAL_PROFILE)) {
      throw denied();
    }
  }

  public void requireWrite(UUID userId) {
    requireUser(userId);
    if (!canWrite(userId)) {
      throw denied();
    }
  }

  public boolean canWrite(UUID userId) {
    var actor = currentActor();
    var identity =
        identities
            .find(actor.tenantId(), actor.userId())
            .orElseThrow(UserContactDetailsAccess::denied);
    if (actor.userId().equals(userId)) {
      return true;
    }
    String role =
        PlatformAuthority.effectiveRoleCode(
            identity.roleCode(), actor.tenantId(), actor.isPlayground(), actor.demoImpersonation());
    var scope =
        permissions
            .evaluateFresh(actor.tenantId(), role, identity.departmentCodes(), actor.userId())
            .scopeOf("members", "write");
    return scope != null && scope.compareTo(DataScope.ORGANIZATION) >= 0;
  }

  private void requireUser(UUID userId) {
    if (!users.existsByTenantIdAndId(TenantContext.requireTenantId(), userId)) {
      throw new PlatformDomainException("User not found", "USER_NOT_FOUND", 404);
    }
  }

  private com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext
      currentActor() {
    UUID tenant = TenantContext.requireTenantId();
    return contexts
        .resolve(SecurityContextHolder.getContext().getAuthentication())
        .filter(actor -> tenant.equals(actor.tenantId()))
        .orElseThrow(UserContactDetailsAccess::denied);
  }

  private static PlatformDomainException denied() {
    return new PlatformDomainException(
        "Authority to access this user's contact details is required",
        "USER_CONTACT_DETAILS_ACCESS_REQUIRED",
        403);
  }
}
