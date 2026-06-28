package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.tenant.dto.GoRealResponse;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.app.UserOnboardingService;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantGoRealService {

  private final TenantSystemService tenantSystemService;
  private final UserRepository userRepository;
  private final UserOnboardingService userOnboardingService;
  private final TenantTransactionalPurgeService purgeService;

  @Transactional
  public GoRealResponse goReal(AuthenticatedUserContext ctx, CompleteOnboardingRequest request) {
    if (ctx == null || ctx.tenantId() == null || ctx.userId() == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    UUID tenantId = ctx.tenantId();
    UUID userId = ctx.userId();
    TenantDto tenant =
        tenantSystemService
            .findById(tenantId)
            .orElseThrow(
                () -> new PlatformDomainException("Tenant not found", "TENANT_NOT_FOUND", 404));
    if (!tenant.isDemoMode()) {
      throw new PlatformDomainException("Tenant is already real", "TENANT_ALREADY_REAL", 409);
    }

    User caller =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(
                () -> new PlatformDomainException("User not found", "USER_NOT_FOUND", 404));
    ensureOwnerCanGoReal(caller);

    TenantContext.executeInTenantContext(
        tenantId, () -> userOnboardingService.completeOnboarding(userId, request));
    TenantTransactionalPurgeService.PurgeResult result = purgeService.goReal(tenantId);
    return new GoRealResponse(
        tenantId, false, result.trialStartedAt(), result.trialEndsAt(), result.deletedRows());
  }

  private void ensureOwnerCanGoReal(User caller) {
    if (caller.isDemoSeed()) {
      throw new PlatformDomainException(
          "Seeded demo users cannot switch the tenant to real mode", "GO_REAL_REQUIRES_OWNER", 403);
    }
    String roleCode = caller.getRole() != null ? caller.getRole().getRoleCode() : null;
    if (!"ADMIN".equalsIgnoreCase(roleCode) && !"PLATFORM_ADMIN".equalsIgnoreCase(roleCode)) {
      throw new PlatformDomainException(
          "Only a tenant owner or admin can switch the tenant to real mode",
          "GO_REAL_REQUIRES_OWNER",
          403);
    }
  }
}
