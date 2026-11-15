package com.fabricmanagement.platform.tradingpartner.security;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContextResolver;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.PlatformAuthority;
import com.fabricmanagement.common.infrastructure.tenant.PackageAccessPort;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.security.PermissionIdentityReader;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PartnerContactDetailsAccess {

  private final AuthenticatedUserContextResolver contexts;
  private final PermissionIdentityReader identities;
  private final PermissionEvaluator permissions;
  private final PackageAccessPort packages;

  public Capability capability(PartnerType partnerType) {
    var tenantId = TenantContext.requireTenantId();
    var actor =
        contexts
            .resolve(SecurityContextHolder.getContext().getAuthentication())
            .filter(context -> tenantId.equals(context.tenantId()))
            .orElse(null);
    if (actor == null || partnerType == null) {
      return new Capability(false, "PARTNER_CONTACT_WRITE_REQUIRED");
    }
    var identity = identities.find(tenantId, actor.userId()).orElse(null);
    if (identity == null) {
      return new Capability(false, "PARTNER_CONTACT_WRITE_REQUIRED");
    }
    String role =
        PlatformAuthority.effectiveRoleCode(
            identity.roleCode(), tenantId, actor.isPlayground(), actor.demoImpersonation());
    if ("ADMIN".equals(role) || "TRADING_PARTNER_MANAGER".equals(role)) {
      return new Capability(true, null);
    }
    if (!partnerType.isCustomer() || !packages.isResourceOpen(tenantId, "sales")) {
      return new Capability(false, "PARTNER_CONTACT_WRITE_REQUIRED");
    }
    var scope =
        permissions
            .evaluateFresh(tenantId, role, identity.departmentCodes(), actor.userId())
            .scopeOf("sales", "write");
    if (scope == null) {
      return new Capability(false, "PARTNER_CONTACT_WRITE_REQUIRED");
    }
    if (scope.compareTo(DataScope.ORGANIZATION) < 0) {
      return new Capability(false, "PARTNER_CONTACT_SCOPE_REQUIRED");
    }
    return new Capability(true, null);
  }

  @Schema(name = "PartnerContactDetailsEditCapability")
  public record Capability(boolean allowed, @Schema(nullable = true) String reason) {}
}
